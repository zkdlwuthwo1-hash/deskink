//! Isolated duplex USB transport diagnostic; synthetic video/pen only.
//! Exercises start_sender, framing, session authorization and AnnotationState.
//! No capture, physical USB service, saved pairing or user's live host is used.
//! Input timing includes the observer's polling/scheduling delay and is an
//! upper bound, not Pencil-to-display latency. The actual send rate may be lower
//! than 240Hz because each event is observed before the next event is sent.
use eternal_host::{
    annotation::{AnnotationState, AnnotationWorkspace, INPUT_MAGIC},
    capture::OutputInfo,
    control::SharedControl,
    encoder::NALUnit,
    transport::{
        self,
        link::{read_datagram, read_preamble, write_datagram},
    },
};
use eternal_wire::v2::{
    control::{
        CAP_DECODE_H264, ControlMessage, Hello2, HelloAck, HelloStatus, Ping, encode_control,
        parse_control,
    },
    media::MediaHeader,
};
use std::{
    sync::{
        Arc, Mutex,
        atomic::{AtomicBool, Ordering},
    },
    time::{Duration, Instant},
};
use tokio::{
    net::{
        TcpListener,
        tcp::{OwnedReadHalf, OwnedWriteHalf},
    },
    sync::mpsc,
};

#[tokio::main(flavor = "multi_thread", worker_threads = 2)]
async fn main() {
    eternal_host::clock::init();
    let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
    // This process alone uses the synthetic tablet. The direct path disables ADB.
    std::env::set_var(
        "ETERNAL_USB_DIRECT",
        listener.local_addr().unwrap().to_string(),
    );
    std::env::set_var("ETERNAL_ABR", "0");
    let annotations = Arc::new(AnnotationState::new(
        OutputInfo {
            adapter_index: 0,
            output_index: 0,
            device_name: "diagnostic".into(),
            width: 1920,
            height: 1080,
            left: 0,
            top: 0,
            is_primary: true,
            adapter_name: "synthetic".into(),
        },
        0,
    ));
    let mut shared = SharedControl::new(0, 15_000_000);
    shared.annotation = Some(Arc::new(AnnotationWorkspace::new(annotations.clone())));
    shared.running.store(true, Ordering::SeqCst);
    shared.pairing.lock().require_usb_code = true;
    let code = shared.pairing.lock().code();
    let (nal_tx, nal_rx) = mpsc::channel(4);
    let (_audio_tx, audio_rx) = mpsc::channel(4);
    let (supervisor_tx, _supervisor_rx) = std::sync::mpsc::channel();
    let worker_shared = shared.clone();
    let worker = tokio::spawn(async move {
        transport::start_sender(nal_rx, audio_rx, 0, worker_shared, supervisor_tx)
            .await
            .map_err(|e| e.to_string())
    });
    let producing = Arc::new(AtomicBool::new(true));
    let producer_running = producing.clone();
    let producer_shared = shared.clone();
    let producer = tokio::spawn(async move {
        let mut sequence = 0;
        let mut tick = tokio::time::interval(Duration::from_micros(16_667));
        tick.set_missed_tick_behavior(tokio::time::MissedTickBehavior::Skip);
        while producer_running.load(Ordering::Relaxed) {
            tick.tick().await;
            sequence += 1;
            let keyframe =
                sequence % 30 == 1 || producer_shared.force_next_idr.swap(false, Ordering::SeqCst);
            if nal_tx
                .send(NALUnit {
                    data: vec![0x65; if keyframe { 180_000 } else { 30_000 }],
                    sequence,
                    timestamp: Instant::now(),
                    encode_duration_us: 0,
                    is_keyframe: keyframe,
                })
                .await
                .is_err()
            {
                break;
            }
        }
    });
    let (mut writer, reader, ack) = connect(&listener, code, [0; 16]).await;
    let stall = Arc::new(AtomicBool::new(false));
    let ages = Arc::new(Mutex::new(Vec::new()));
    let receiver = tokio::spawn(read_video(reader, stall.clone(), ages.clone()));
    let mut sequence = 0;
    measure(
        "initial",
        &mut writer,
        &ack,
        &annotations,
        &mut sequence,
        &ages,
    )
    .await;
    stall.store(true, Ordering::Relaxed);
    measure(
        "repeated-100ms-reader-stalls",
        &mut writer,
        &ack,
        &annotations,
        &mut sequence,
        &ages,
    )
    .await;
    stall.store(false, Ordering::Relaxed);
    tokio::time::sleep(Duration::from_millis(300)).await;
    measure(
        "same-connection-after-stalls",
        &mut writer,
        &ack,
        &annotations,
        &mut sequence,
        &ages,
    )
    .await;
    receiver.abort();
    drop(writer);
    let (mut writer, reader, ack) = connect(&listener, 0, ack.auth_token).await;
    let receiver = tokio::spawn(read_video(reader, stall, ages.clone()));
    sequence = 0;
    measure(
        "after-app-reconnect",
        &mut writer,
        &ack,
        &annotations,
        &mut sequence,
        &ages,
    )
    .await;
    receiver.abort();
    drop(writer);
    producing.store(false, Ordering::Relaxed);
    shared.stop();
    producer.await.unwrap();
    tokio::time::timeout(Duration::from_secs(3), worker)
        .await
        .unwrap()
        .unwrap()
        .unwrap();
}

async fn connect(
    listener: &TcpListener,
    code: u32,
    token: [u8; 16],
) -> (OwnedWriteHalf, OwnedReadHalf, HelloAck) {
    let (mut socket, _) = tokio::time::timeout(Duration::from_secs(5), listener.accept())
        .await
        .unwrap()
        .unwrap();
    socket.set_nodelay(true).unwrap();
    read_preamble(&mut socket).await.unwrap();
    let hello = Hello2 {
        proto_min: 2,
        proto_max: 2,
        client_nonce: 1,
        listen_port: 9877,
        decoder_caps: CAP_DECODE_H264,
        feature_caps: 0,
        screen_px_w: 1920,
        screen_px_h: 1080,
        screen_pt_w: 960,
        screen_pt_h: 540,
        refresh_hz: 60,
        device_name: "Synthetic tablet".into(),
        device_id: 7654,
        preferred_fps: 60,
        auth_token: token,
        pairing_code: code,
    };
    write_datagram(
        &mut socket,
        &encode_control(0, 1, &ControlMessage::Hello2(hello)),
    )
    .await
    .unwrap();
    let ack = tokio::time::timeout(Duration::from_secs(3), async {
        loop {
            let bytes = read_datagram(&mut socket).await.unwrap();
            if let Ok((_, ControlMessage::HelloAck(ack))) = parse_control(&bytes) {
                break ack;
            }
        }
    })
    .await
    .unwrap();
    assert_eq!(ack.status, HelloStatus::Ok);
    let (reader, writer) = socket.into_split();
    (writer, reader, ack)
}

async fn read_video(mut reader: OwnedReadHalf, stall: Arc<AtomicBool>, ages: Arc<Mutex<Vec<f64>>>) {
    let mut frames = 0;
    while let Ok(packet) = read_datagram(&mut reader).await {
        if let Ok((header, _)) = MediaHeader::decode(&packet) {
            if header.frag_index + 1 == header.frag_count {
                let age = eternal_host::clock::host_now_us().saturating_sub(header.capture_ts_us)
                    as f64
                    / 1000.0;
                ages.lock().unwrap().push(age);
                frames += 1;
                if stall.load(Ordering::Relaxed) && frames % 10 == 0 {
                    tokio::time::sleep(Duration::from_millis(100)).await;
                }
            }
        }
    }
}

async fn measure(
    label: &str,
    writer: &mut OwnedWriteHalf,
    ack: &HelloAck,
    annotations: &AnnotationState,
    sequence: &mut u64,
    ages: &Mutex<Vec<f64>>,
) {
    ages.lock().unwrap().clear();
    let mut samples = Vec::new();
    let mut tick = tokio::time::interval(Duration::from_micros(4_167));
    tick.set_missed_tick_behavior(tokio::time::MissedTickBehavior::Skip);
    for _ in 0..720 {
        tick.tick().await;
        *sequence += 1;
        let envelope = serde_json::to_vec(&serde_json::json!({
            "version":1,"session":ack.session_id,"epoch":ack.stream_config.stream_epoch,"seq":*sequence,
            "command":{"type":"pointer","point":{"x":(*sequence%1900),"y":100,"pressure":0.5},
            "color":[255,80,40,255],"radius":5,"laser":false}
        })).unwrap();
        let mut packet = INPUT_MAGIC.to_vec();
        packet.extend(envelope);
        let began = Instant::now();
        write_datagram(writer, &packet).await.unwrap();
        tokio::time::timeout(Duration::from_millis(500), async {
            while annotations.ink.lock().accepted_sequence() != *sequence {
                tokio::time::sleep(Duration::from_millis(1)).await;
            }
        })
        .await
        .expect("Pen input did not reach the Windows board within 500ms");
        samples.push(began.elapsed().as_secs_f64() * 1000.0);
        if *sequence % 120 == 0 {
            write_datagram(
                writer,
                &encode_control(
                    ack.session_id,
                    *sequence as u32 + 2,
                    &ControlMessage::Ping(Ping {
                        t1_us: eternal_host::clock::host_now_us(),
                    }),
                ),
            )
            .await
            .unwrap();
        }
    }
    samples.sort_by(f64::total_cmp);
    let mut video = std::mem::take(&mut *ages.lock().unwrap());
    video.sort_by(f64::total_cmp);
    assert!(!video.is_empty(), "No video delivered");
    println!(
        "{label}: pen_ms p50={:.2} p95={:.2} max={:.2} above_50ms={}; video_ms p50={:.2} p95={:.2} max={:.2} above_100ms={} frames={}",
        samples[360],
        samples[684],
        samples[719],
        samples.iter().filter(|&&age| age > 50.0).count(),
        video[video.len() / 2],
        video[video.len() * 95 / 100],
        video[video.len() - 1],
        video.iter().filter(|&&age| age > 100.0).count(),
        video.len()
    );
    assert!(samples[684] < 50.0, "Pen transport p95 exceeded 50ms");
    if !label.contains("reader-stalls") {
        assert!(
            video[video.len() * 95 / 100] < 100.0,
            "Recovered video p95 exceeded 100ms"
        );
    }
}
