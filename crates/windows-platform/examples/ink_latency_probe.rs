//! Local 1080p annotation rendering diagnostic. Saves timings, never desktop pixels.
use deskink_core::{Board, Brush, Command, Point};
use deskink_windows::overlay::Overlay;
use std::time::Instant;

fn main() {
    let long_session = std::env::args().any(|arg| arg == "--long-session");
    let overlay = Overlay::new(0, 0, 1920, 1080).unwrap();
    let mut board = Board::new();
    let brush = Brush {
        color: [255, 130, 30, 255],
        width: 2.0,
    };
    let mut failed = false;
    let counts: &[u64] = if long_session {
        &[1, 100, 1000]
    } else {
        &[1, 20, 100]
    };
    let samples = if long_session { 300 } else { 100 };
    for &count in counts {
        while (board.strokes().len() as u64) < count {
            let id = board.strokes().len() as u64 + 1;
            for sample in 0..samples {
                let point = Point {
                    x: 40.0 + sample as f32 * 4.0,
                    y: 80.0 + (id % 20) as f32 * 8.0 + (sample as f32 * 0.3).sin() * 3.0,
                    pressure: 0.5,
                };
                board
                    .apply(if sample == 0 {
                        Command::Begin { id, brush, point }
                    } else {
                        Command::Append { id, point }
                    })
                    .unwrap();
            }
            board.apply(Command::End { id }).unwrap();
        }
        let mut timings = Vec::new();
        for frame in 0..120 {
            board
                .apply(Command::Pointer {
                    point: Some(Point {
                        x: 60.0 + frame as f32 * 2.0,
                        y: 280.0,
                        pressure: 0.5,
                    }),
                    color: [255, 60, 30, 255],
                    radius: 5.0,
                    laser: false,
                })
                .unwrap();
            let started = Instant::now();
            // The real app snapshots visible ink under its input lock before
            // presenting it. Include that cost in the long-session probe.
            if long_session {
                overlay.present(&board.snapshot_for_display()).unwrap();
            } else {
                overlay.present(&board).unwrap();
            }
            timings.push(started.elapsed().as_secs_f64() * 1000.0);
        }
        timings.sort_by(f64::total_cmp);
        println!(
            "1080p ink strokes={count} points={} p50_ms={:.2} p95_ms={:.2} max_ms={:.2}",
            count * samples as u64,
            timings[60],
            timings[114],
            timings[119]
        );
        failed |= timings[114] > 16.7;
    }
    drop(overlay);
    assert!(
        !failed,
        "Ink rendering exceeded the 60 Hz frame budget (16.7ms p95)"
    );
}
