//! Real UDP coverage of the public transport entry point, without capture or a GPU.
//! Run with ETERNAL_USB_DIRECT=127.0.0.1:9 so neither Apple nor ADB devices are opened.
//! Local-interface delivery is a protocol test, not a physical Wi-Fi latency measurement.

use std::net::{IpAddr, Ipv4Addr, SocketAddr};
use std::sync::atomic::Ordering;
use std::sync::{mpsc as std_mpsc, Arc};
use std::time::{Duration, Instant};

use eternal_host::annotation::{AnnotationState, AnnotationWorkspace};
use eternal_host::capture::OutputInfo;
use eternal_host::control::SharedControl;
use eternal_host::encoder::NALUnit;
use eternal_host::transport::start_sender;
use eternal_wire::v2::control::{
    encode_control, parse_control, ControlMessage, Hello2, HelloAck, HelloStatus, CAP_DECODE_H264,
};
use eternal_wire::v2::media::MediaHeader;
use serde_json::{json, Value};
use tokio::net::UdpSocket;
use tokio::sync::mpsc;
use tokio::task::JoinHandle;
use tokio::time::timeout;

struct RunningTransport {
    shared: SharedControl,
    board: Arc<AnnotationState>,
    port: u16,
    nal: mpsc::Sender<NALUnit>,
    _audio: mpsc::Sender<eternal_host::audio::SessionPacket>,
    task: Option<JoinHandle<Result<(), String>>>,
}

impl RunningTransport {
    fn start(lan_enabled: bool) -> Self {
        // Reserve a free port, then hand it to the public start_sender interface.
        // Startup HELLO retries below handle scheduling without a fixed sleep.
        let reservation = std::net::UdpSocket::bind("0.0.0.0:0").unwrap();
        let port = reservation.local_addr().unwrap().port();
        let board = Arc::new(AnnotationState::new(
            OutputInfo {
                adapter_index: 0,
                output_index: 0,
                device_name: "test-display".into(),
                width: 640,
                height: 360,
                left: 0,
                top: 0,
                is_primary: true,
                adapter_name: "test".into(),
            },
            0,
        ));
        let mut shared = SharedControl::new(port, 4_000_000);
        assert!(!shared.lan_enabled.load(Ordering::SeqCst));
        shared.annotation = Some(Arc::new(AnnotationWorkspace::new(board.clone())));
        shared.lan_enabled.store(lan_enabled, Ordering::SeqCst);
        shared.running.store(true, Ordering::SeqCst);
        shared.pairing.lock().require_usb_code = true;
        let (nal, nal_rx) = mpsc::channel(2);
        let (audio, audio_rx) = mpsc::channel(2);
        let (supervisor, _supervisor_rx) = std_mpsc::channel();
        let control = shared.clone();
        drop(reservation);
        let task = tokio::spawn(async move {
            start_sender(nal_rx, audio_rx, port, control, supervisor)
                .await
                .map_err(|error| error.to_string())
        });
        Self {
            shared,
            board,
            port,
            nal,
            _audio: audio,
            task: Some(task),
        }
    }

    async fn stop(mut self) {
        self.shared.stop();
        let mut task = self.task.take().unwrap();
        let completed = timeout(Duration::from_secs(2), &mut task).await;
        if completed.is_err() {
            task.abort();
        }
        completed
            .expect("transport did not stop")
            .expect("transport panicked")
            .expect("transport failed");
    }
}

impl Drop for RunningTransport {
    fn drop(&mut self) {
        self.shared.stop();
        if let Some(task) = self.task.take() {
            task.abort();
        }
    }
}

fn hello(client: &UdpSocket, nonce: u32, code: u32, reliable_ink: bool) -> Hello2 {
    Hello2 {
        proto_min: 2,
        proto_max: 2,
        client_nonce: nonce,
        listen_port: client.local_addr().unwrap().port(),
        decoder_caps: CAP_DECODE_H264,
        // Independent negotiated wire values: input, monitor boards, reliable ink.
        feature_caps: 0x0009 | if reliable_ink { 0x0010 } else { 0 },
        screen_px_w: 640,
        screen_px_h: 360,
        screen_pt_w: 640,
        screen_pt_h: 360,
        refresh_hz: 60,
        device_name: "LAN test tablet".into(),
        device_id: 123,
        preferred_fps: 60,
        auth_token: [0; 16],
        pairing_code: code,
    }
}

async fn receive_matching<T>(
    socket: &UdpSocket,
    source: SocketAddr,
    budget: Duration,
    mut accept: impl FnMut(&[u8]) -> Option<T>,
) -> Option<T> {
    let deadline = tokio::time::Instant::now() + budget;
    let mut bytes = [0; 2048];
    loop {
        match tokio::time::timeout_at(deadline, socket.recv_from(&mut bytes)).await {
            Ok(Ok((length, peer))) => {
                if peer == source {
                    if let Some(value) = accept(&bytes[..length]) {
                        return Some(value);
                    }
                }
            }
            // Windows may surface an ICMP refusal if HELLO races the initial bind.
            Ok(Err(error))
                if matches!(
                    error.kind(),
                    std::io::ErrorKind::ConnectionReset | std::io::ErrorKind::ConnectionRefused
                ) =>
            {
                continue
            }
            Ok(Err(error)) => panic!("UDP receive failed: {error}"),
            Err(_) => return None,
        }
    }
}

async fn exchange_hello(socket: &UdpSocket, target: SocketAddr, request: Hello2) -> HelloAck {
    let nonce = request.client_nonce;
    let bytes = encode_control(0, 1, &ControlMessage::Hello2(request));
    for _ in 0..15 {
        socket.send_to(&bytes, target).await.unwrap();
        if let Some(ack) = receive_matching(socket, target, Duration::from_millis(200), |bytes| {
            match parse_control(bytes).ok()?.1 {
                ControlMessage::HelloAck(ack) if ack.client_nonce == nonce => Some(ack),
                _ => None,
            }
        })
        .await
        {
            return ack;
        }
    }
    panic!("no matching HELLO_ACK within startup/retry budget");
}

fn ink(session: u32, epoch: u32, sequence: u64, command: Value) -> Vec<u8> {
    let mut packet = b"DI02".to_vec();
    packet.extend(
        serde_json::to_vec(&json!({
            "version": 1, "session": session, "epoch": epoch,
            "seq": sequence, "command": command
        }))
        .unwrap(),
    );
    packet
}

fn ack_sequence(bytes: &[u8], session: u32, epoch: u32) -> Option<u64> {
    if bytes.len() != 20 || &bytes[..4] != b"DA02" {
        return None;
    }
    assert_eq!(u32::from_le_bytes(bytes[4..8].try_into().unwrap()), session);
    assert_eq!(u32::from_le_bytes(bytes[8..12].try_into().unwrap()), epoch);
    Some(u64::from_le_bytes(bytes[12..20].try_into().unwrap()))
}

async fn expect_ink_ack(
    socket: &UdpSocket,
    target: SocketAddr,
    session: u32,
    epoch: u32,
    seq: u64,
) {
    assert_eq!(
        receive_matching(socket, target, Duration::from_secs(1), |bytes| {
            ack_sequence(bytes, session, epoch)
        })
        .await,
        Some(seq),
        "ACK must report the highest contiguous applied command"
    );
}

#[tokio::test(flavor = "multi_thread", worker_threads = 2)]
async fn lan_is_opt_in_and_pairs_streams_and_applies_only_authenticated_ordered_ink() {
    assert_eq!(
        std::env::var("ETERNAL_USB_DIRECT").as_deref(),
        Ok("127.0.0.1:9"),
        "Set ETERNAL_USB_DIRECT=127.0.0.1:9 before running this test; real devices must not be contacted"
    );
    eternal_host::clock::init();
    let detected: SocketAddr = eternal_host::gui::detect_local_ip(0)
        .parse()
        .expect("LAN test needs an active local IPv4 adapter");
    let local_ip = detected.ip();
    assert!(matches!(local_ip, IpAddr::V4(ip) if !ip.is_loopback() && !ip.is_unspecified()));

    // Prove the default listener is running before testing that it is unreachable
    // via the external interface; a not-yet-started task must not make this pass.
    let disabled = RunningTransport::start(false);
    let loopback = UdpSocket::bind((Ipv4Addr::LOCALHOST, 0)).await.unwrap();
    let loopback_target = SocketAddr::from((Ipv4Addr::LOCALHOST, disabled.port));
    let rejected = exchange_hello(&loopback, loopback_target, hello(&loopback, 1, 0, true)).await;
    assert_eq!(rejected.status, HelloStatus::Unauthorized);
    let external = UdpSocket::bind(SocketAddr::new(local_ip, 0)).await.unwrap();
    let disabled_target = SocketAddr::new(local_ip, disabled.port);
    let disabled_request = hello(&external, 2, disabled.shared.pairing.lock().code(), true);
    external
        .send_to(
            &encode_control(0, 1, &ControlMessage::Hello2(disabled_request)),
            disabled_target,
        )
        .await
        .unwrap();
    assert!(receive_matching(
        &external,
        disabled_target,
        Duration::from_millis(250),
        |bytes| { parse_control(bytes).ok() }
    )
    .await
    .is_none());
    assert!(!disabled.shared.session.lock().is_active());
    disabled.stop().await;

    let enabled = RunningTransport::start(true);
    let target = SocketAddr::new(local_ip, enabled.port);
    let client = UdpSocket::bind(SocketAddr::new(local_ip, 0)).await.unwrap();
    let code = enabled.shared.pairing.lock().code();
    let old = exchange_hello(&client, target, hello(&client, 10, code, false)).await;
    assert_eq!(old.status, HelloStatus::VersionUnsupported);
    assert_eq!(old.session_id, 0);
    let wrong_code = if code == 999_999 { 1 } else { code + 1 };
    let wrong = exchange_hello(&client, target, hello(&client, 11, wrong_code, true)).await;
    assert_eq!(wrong.status, HelloStatus::Unauthorized);
    assert_eq!(wrong.session_id, 0);
    let paired = exchange_hello(&client, target, hello(&client, 12, code, true)).await;
    assert_eq!(paired.status, HelloStatus::Ok);
    assert_ne!(paired.session_id, 0);
    assert_eq!(paired.host_caps & 0xF000, 0xF000);
    assert_eq!(
        (paired.stream_config.width, paired.stream_config.height),
        (640, 360)
    );
    let session = paired.session_id;
    let epoch = paired.stream_config.stream_epoch;
    assert_ne!(epoch, 0);

    // Verify public encoder-output -> UDP fragmentation bytes, independently of
    // hardware decoding. This deliberately synthetic access unit is not an H.264 quality test.
    let mut payload: Vec<u8> = (0..3000).map(|index| (index % 251) as u8).collect();
    payload[..5].copy_from_slice(&[0, 0, 0, 1, 0x65]);
    let captured = Instant::now();
    enabled
        .nal
        .send(NALUnit {
            data: payload.clone(),
            sequence: 7,
            timestamp: captured,
            encode_duration_us: 0,
            is_keyframe: true,
        })
        .await
        .unwrap();
    let mut fragments = std::collections::BTreeMap::new();
    for _ in 0..3 {
        let (header, data) = receive_matching(&client, target, Duration::from_secs(1), |bytes| {
            let (header, body) = MediaHeader::decode(bytes).ok()?;
            assert!(bytes.len() <= 1400);
            Some((header, body.to_vec()))
        })
        .await
        .expect("paired client did not receive synthetic video");
        assert_eq!(header.session_id, session);
        assert_eq!(header.stream_epoch, epoch);
        assert_eq!(header.frame_seq, 7);
        assert_eq!(header.frag_count, 3);
        assert_eq!(
            header.capture_ts_us,
            eternal_host::clock::instant_to_us(captured)
        );
        assert!(header.is_keyframe);
        assert!(!header.is_retransmit);
        assert!(fragments.insert(header.frag_index, data).is_none());
    }
    assert_eq!(
        fragments.into_values().flatten().collect::<Vec<_>>(),
        payload
    );

    let begin = json!({"type":"begin","id":7,"brush":{"color":[255,80,40,255],"width":4},
        "point":{"x":100,"y":200,"pressure":0.4}});
    let first = ink(session, epoch, 1, begin.clone());
    let stranger = UdpSocket::bind(SocketAddr::new(local_ip, 0)).await.unwrap();
    stranger.send_to(&first, target).await.unwrap();
    assert!(
        receive_matching(&stranger, target, Duration::from_millis(100), |bytes| {
            ack_sequence(bytes, session, epoch)
        })
        .await
        .is_none()
    );
    assert!(enabled.board.ink.lock().board().strokes().is_empty());

    // Wrong binding from the right socket also cannot begin a stroke.
    for invalid in [
        ink(session.wrapping_add(1), epoch, 1, begin.clone()),
        ink(session, epoch.wrapping_add(1), 1, begin),
    ] {
        client.send_to(&invalid, target).await.unwrap();
    }
    assert!(
        receive_matching(&client, target, Duration::from_millis(100), |bytes| {
            ack_sequence(bytes, session, epoch)
        })
        .await
        .is_none()
    );
    assert!(enabled.board.ink.lock().board().strokes().is_empty());

    let second = ink(
        session,
        epoch,
        2,
        json!({"type":"append","id":7,"point":{"x":120,"y":215,"pressure":0.5}}),
    );
    client.send_to(&second, target).await.unwrap();
    expect_ink_ack(&client, target, session, epoch, 0).await;
    assert!(enabled.board.ink.lock().board().strokes().is_empty());
    client.send_to(&first, target).await.unwrap();
    expect_ink_ack(&client, target, session, epoch, 2).await;
    {
        let document = enabled.board.ink.lock();
        assert_eq!(document.board().strokes().len(), 1);
        assert_eq!(document.board().strokes()[0].points.len(), 2);
        assert_eq!(document.board().strokes()[0].points[1].x, 120.0);
    }
    // A lost ACK causes exact duplicates; they cannot append points twice.
    client.send_to(&first, target).await.unwrap();
    expect_ink_ack(&client, target, session, epoch, 2).await;
    client.send_to(&second, target).await.unwrap();
    expect_ink_ack(&client, target, session, epoch, 2).await;
    let end = ink(session, epoch, 3, json!({"type":"end","id":7}));
    client.send_to(&end, target).await.unwrap();
    expect_ink_ack(&client, target, session, epoch, 3).await;
    {
        let document = enabled.board.ink.lock();
        assert_eq!(document.board().strokes()[0].points.len(), 2);
        assert!(document.board().strokes()[0].finished);
    }
    let undo = ink(session, epoch, 4, json!({"type":"undo"}));
    for _ in 0..2 {
        client.send_to(&undo, target).await.unwrap();
        expect_ink_ack(&client, target, session, epoch, 4).await;
        assert!(enabled.board.ink.lock().board().strokes().is_empty());
    }
    client
        .send_to(&ink(session, epoch, 5, json!({"type":"redo"})), target)
        .await
        .unwrap();
    expect_ink_ack(&client, target, session, epoch, 5).await;
    assert_eq!(enabled.board.ink.lock().board().strokes().len(), 1);
    enabled.stop().await;
}
