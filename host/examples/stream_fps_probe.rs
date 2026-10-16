//! Local hardware diagnostic. No sockets, saved pixels, or device credentials.
//! Run with a moving desktop; --encode-only substitutes a paced BGRA source.
#[cfg(windows)]
fn main() {
    probe::run();
}
#[cfg(not(windows))]
fn main() {
    eprintln!("This diagnostic requires an interactive Windows desktop.");
}

#[cfg(windows)]
mod probe {
    use eternal_host::{
        annotation::AnnotationState,
        capture::{self, RawFrame},
        control::SharedControl,
        encoder,
        gpu::GpuInfo,
        stats::PIPELINE_STATS,
        transport::{link::PeerId, session::ConfigSource},
    };
    use eternal_wire::v2::control::{ControlMessage, Hello2, StreamConfig, CAP_DECODE_H264};
    use std::{
        sync::{atomic::Ordering, Arc},
        time::{Duration, Instant},
    };

    struct ProbeConfig;
    impl ConfigSource for ProbeConfig {
        fn host_name(&self) -> String {
            "Local FPS probe".into()
        }
        fn stream_config(&self) -> StreamConfig {
            StreamConfig {
                stream_epoch: 1,
                width: 1920,
                height: 1080,
                fps: 60,
                codec: 0,
                flags: 0,
                bitrate_bps: 15_000_000,
            }
        }
    }

    pub fn run() {
        eternal_host::clock::init();
        ffmpeg_next::init().unwrap();
        let encode_only = std::env::args().any(|arg| arg == "--encode-only");
        let minimum_fps: Option<f64> = std::env::args().find_map(|arg| {
            arg.strip_prefix("--minimum-fps=")
                .map(|value| value.parse().unwrap())
        });
        let maximum_age: Option<f64> = std::env::args().find_map(|arg| {
            arg.strip_prefix("--maximum-frame-age-ms=")
                .map(|value| value.parse().unwrap())
        });
        let mut shared = SharedControl::new(0, 15_000_000);
        shared.running.store(true, Ordering::SeqCst);
        let output = capture::enumerate_outputs()
            .into_iter()
            .find(|o| o.is_primary)
            .unwrap();
        let overlay = deskink_windows::overlay::Overlay::new(
            output.left,
            output.top,
            output.width,
            output.height,
        )
        .unwrap();
        shared.annotation = Some(Arc::new(AnnotationState::new(
            output,
            overlay.hwnd().0 as usize,
        )));
        shared.session.lock().handle_control(
            PeerId::usb(99),
            0,
            ControlMessage::Hello2(Hello2 {
                proto_min: 2,
                proto_max: 2,
                client_nonce: 1,
                listen_port: 0,
                decoder_caps: CAP_DECODE_H264,
                feature_caps: 0,
                screen_px_w: 1920,
                screen_px_h: 1080,
                screen_pt_w: 960,
                screen_pt_h: 540,
                refresh_hz: 60,
                device_name: "Local probe".into(),
                device_id: 0,
                preferred_fps: 60,
                auth_token: [0; 16],
                pairing_code: 0,
            }),
            &ProbeConfig,
            Instant::now(),
        );
        assert!(shared.client_connected());
        let generation = eternal_host::supervisor::CURRENT_GENERATION.load(Ordering::SeqCst);
        let (producer, consumer) = capture::frame_slot();
        let (tx, mut rx) = tokio::sync::mpsc::channel(encoder::NAL_CHANNEL_CAPACITY);
        let encode_shared = shared.clone();
        let encode = std::thread::spawn(move || {
            encoder::run_encode_stage(consumer, tx, encode_shared, GpuInfo::detect(), generation)
                .map_err(|e| e.to_string())
                .unwrap();
        });
        let started = Instant::now();
        let collector = std::thread::spawn(move || {
            let mut ages = Vec::new();
            while let Some(nal) = rx.blocking_recv() {
                if started.elapsed() > Duration::from_secs(2) {
                    ages.push(nal.timestamp.elapsed().as_secs_f64() * 1000.0);
                }
            }
            ages.sort_by(f64::total_cmp);
            if !ages.is_empty() {
                println!(
                    "capture-to-encoded ms: p50={:.1} p95={:.1}, packets={}",
                    ages[ages.len() / 2],
                    ages[ages.len() * 95 / 100],
                    ages.len()
                );
            }
            if let Some(maximum) = maximum_age {
                assert!(!ages.is_empty());
                assert!(
                    ages[ages.len() * 95 / 100] <= maximum,
                    "capture-to-encoded p95 {:.1} ms > {maximum}",
                    ages[ages.len() * 95 / 100]
                );
            }
        });
        let capture_shared = shared.clone();
        let capture = std::thread::spawn(move || {
            if encode_only {
                let data = Arc::new(vec![128u8; 1920 * 1080 * 4]);
                let mut number = 0;
                let mut deadline = Instant::now();
                while capture_shared.running.load(Ordering::SeqCst) {
                    producer.publish(RawFrame {
                        frame_number: number,
                        timestamp: Instant::now(),
                        data: data.clone(),
                        width: 1920,
                        height: 1080,
                    });
                    PIPELINE_STATS.lock().record_capture(1920, 1080);
                    number += 1;
                    deadline += Duration::from_nanos(1_000_000_000 / 60);
                    std::thread::sleep(deadline.saturating_duration_since(Instant::now()));
                }
                producer.close();
            } else {
                capture::run_capture_stage(producer, capture_shared, 0, generation)
                    .map_err(|e| e.to_string())
                    .unwrap();
            }
        });
        let mut measured_fps = Vec::new();
        for second in 0..8 {
            std::thread::sleep(Duration::from_secs(1));
            let stats = PIPELINE_STATS.lock();
            println!(
                "capture={:.1} encode={:.1} codec_us={} capture_count={} encode_count={}",
                stats.capture_fps,
                stats.encode_fps,
                stats.encode_time_us,
                stats.capture_frame_count,
                stats.encode_frame_count
            );
            if second >= 2 {
                measured_fps.push(stats.encode_fps);
            }
        }
        shared.stop();
        capture.join().unwrap();
        encode.join().unwrap();
        collector.join().unwrap();
        measured_fps.sort_by(f64::total_cmp);
        let median = measured_fps[measured_fps.len() / 2];
        if let Some(minimum) = minimum_fps {
            assert!(
                median >= minimum,
                "moving desktop median {median:.1} fps < {minimum}"
            );
        }
    }
}
