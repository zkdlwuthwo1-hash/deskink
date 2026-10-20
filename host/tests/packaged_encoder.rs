//! Exercise the real encode stage with the LGPL DLLs used by the Windows ZIP.
//! These tests must run before any test-only GPL runtime is added to PATH.
#![cfg(windows)]

use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};

use eternal_host::capture::{frame_slot, RawFrame};
use eternal_host::control::SharedControl;
use eternal_host::encoder::input::EncoderInput;
use eternal_host::encoder::{run_encode_stage, NALUnit, NAL_CHANNEL_CAPACITY};
use eternal_host::gpu::GpuInfo;
use eternal_host::stats::PIPELINE_STATS;
use eternal_wire::h264::contains_nal_type;

static STAGE_LOCK: Mutex<()> = Mutex::new(());

struct StageRun {
    packets: Vec<NALUnit>,
    software_fallback: bool,
    reconnect_after: usize,
}

fn encode(gpu: GpuInfo, input: EncoderInput) -> StageRun {
    ffmpeg_next::init().unwrap();
    let (producer, consumer) = frame_slot();
    let (tx, mut rx) = tokio::sync::mpsc::channel(NAL_CHANNEL_CAPACITY);
    let shared = SharedControl::new(0, 4_000_000);
    *shared.encoder_input.lock() = input;
    shared.running.store(true, Ordering::SeqCst);
    let requests = shared.clone();
    let generation = eternal_host::supervisor::CURRENT_GENERATION.load(Ordering::SeqCst);
    let stage = std::thread::spawn(move || {
        run_encode_stage(consumer, tx, shared, gpu, generation).map_err(|e| e.to_string())
    });
    let received = Arc::new(AtomicUsize::new(0));
    let collector_received = Arc::clone(&received);
    let collector = std::thread::spawn(move || {
        let mut packets = Vec::new();
        while let Some(packet) = rx.blocking_recv() {
            packets.push(packet);
            collector_received.store(packets.len(), Ordering::Release);
        }
        packets
    });
    // A known red sample checks chroma as well as luma, catching incorrect
    // BGRA-to-NV12 packing even when the bitstream itself still decodes.
    let data = Arc::new([20, 40, 200, 255].repeat(640 * 360));
    let mut reconnect_after = None;
    for frame_number in 0..90 {
        if stage.is_finished() {
            break;
        }
        let count = received.load(Ordering::Acquire);
        // Wait for actual output, not a capture number: startup can be slow
        // and FrameSlot intentionally replaces frames while the encoder opens.
        if count >= 5 && reconnect_after.is_none() {
            reconnect_after = Some(count);
            requests.force_next_idr.store(true, Ordering::SeqCst);
        }
        if count >= 35 {
            break;
        }
        producer.publish(RawFrame {
            frame_number,
            timestamp: Instant::now(),
            data: Arc::clone(&data),
            width: 640,
            height: 360,
        });
        std::thread::sleep(Duration::from_millis(20));
    }
    producer.close();
    let result = stage.join();
    let packets = collector.join().unwrap();
    result
        .expect("encode stage panicked")
        .expect("no video output");
    assert!(packets.len() >= 5, "only {} video packets", packets.len());
    StageRun {
        packets,
        software_fallback: PIPELINE_STATS.lock().using_software_fallback,
        reconnect_after: reconnect_after.expect("no warm stream to reconnect"),
    }
}

fn assert_decodable_red_video(packets: &[NALUnit]) {
    for kind in [7, 8, 5] {
        assert!(
            contains_nal_type(&packets[0].data, kind),
            "first frame must include SPS, PPS and IDR"
        );
    }
    let codec = ffmpeg_next::decoder::find(ffmpeg_next::codec::Id::H264).unwrap();
    let mut options = ffmpeg_next::Dictionary::new();
    options.set("err_detect", "explode");
    let mut decoder = ffmpeg_next::codec::Context::new_with_codec(codec)
        .decoder()
        .open_as_with(codec, options)
        .unwrap()
        .video()
        .unwrap();
    let mut frame = ffmpeg_next::frame::Video::empty();
    let mut count = 0;
    let mut receive = |decoder: &mut ffmpeg_next::decoder::Video| loop {
        match decoder.receive_frame(&mut frame) {
            Ok(()) => {
                assert_eq!((frame.width(), frame.height()), (640, 360));
                assert_eq!(frame.format(), ffmpeg_next::format::Pixel::YUV420P);
                // BT.601 limited-range values for RGB(200,40,20), allowing
                // normal H.264 quantization error.
                for (plane, expected) in [(0, 90i16), (1, 96), (2, 200)] {
                    let actual = frame.data(plane)[16 * frame.stride(plane) + 16] as i16;
                    assert!((actual - expected).abs() <= 8, "plane {plane}: {actual}");
                }
                count += 1;
            }
            Err(ffmpeg_next::Error::Eof) => break,
            Err(ffmpeg_next::Error::Other { errno }) if errno == ffmpeg_next::error::EAGAIN => {
                break
            }
            Err(error) => panic!("invalid video: {error}"),
        }
    };
    for packet in packets {
        decoder
            .send_packet(&ffmpeg_next::Packet::copy(&packet.data))
            .unwrap();
        receive(&mut decoder);
    }
    decoder.send_eof().unwrap();
    receive(&mut decoder);
    assert_eq!(count, packets.len(), "a video packet could not be decoded");
}

fn assert_reconnect_video(run: &StageRun) {
    assert_decodable_red_video(&run.packets);
    let offset = run.packets[run.reconnect_after..]
        .iter()
        .position(|p| p.is_keyframe)
        .expect("reconnect received no keyframe");
    // This is well before the normal 30-frame GOP. Inspect output order:
    // NALUnit.sequence labels the capture loop, not delayed codec packet PTS.
    assert!(
        offset <= 5,
        "reconnect waited {offset} output frames for an IDR"
    );
    assert_decodable_red_video(&run.packets[run.reconnect_after + offset..]);
}

#[test]
fn nv12_frames_produce_decodable_video_with_the_packaged_runtime() {
    let _guard = STAGE_LOCK.lock().unwrap_or_else(|e| e.into_inner());
    let gpu = GpuInfo {
        encoder_name: "h264_mf".into(),
        codec_display_name: "H.264 (Media Foundation)".into(),
        ..GpuInfo::software_fallback()
    };
    let run = encode(gpu, EncoderInput::Auto);
    assert!(!run.software_fallback);
    assert_decodable_red_video(&run.packets);
}

#[test]
fn intel_stream_produces_video_even_without_quick_sync_hardware() {
    let _guard = STAGE_LOCK.lock().unwrap_or_else(|e| e.into_inner());
    let gpu = GpuInfo {
        encoder_name: "h264_qsv".into(),
        codec_display_name: "H.264 (QSV)".into(),
        ..GpuInfo::software_fallback()
    };
    let run = encode(gpu, EncoderInput::default());
    eprintln!(
        "Intel path used software fallback: {}",
        run.software_fallback
    );
    assert_reconnect_video(&run);
}
