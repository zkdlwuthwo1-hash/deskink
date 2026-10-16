//! Hardware acceptance probe. A temporary red stroke is shown, captured, then
//! excluded ONLY from this WGC session. No screenshots leave the machine.
use deskink_core::{Board, Brush, Command, Point};
use deskink_windows::{capture::MonitorCapture, overlay::Overlay};
use std::{
    sync::mpsc,
    time::{Duration, Instant},
};
use windows::{
    core::Result,
    Foundation::TypedEventHandler,
    Win32::{
        Graphics::Gdi::{GetDC, GetPixel, ReleaseDC},
        System::WinRT::{RoInitialize, RoUninitialize, RO_INIT_MULTITHREADED},
        UI::{
            HiDpi::{SetProcessDpiAwarenessContext, DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2},
            WindowsAndMessaging::*,
        },
    },
};

fn main() -> Result<()> {
    unsafe {
        let _ = SetProcessDpiAwarenessContext(DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2);
        RoInitialize(RO_INIT_MULTITHREADED)?;
    }
    let result = probe();
    unsafe {
        RoUninitialize();
    }
    result
}

fn probe() -> Result<()> {
    let mut capture = MonitorCapture::primary()?;
    let size = capture.item.Size()?;
    let overlay = Overlay::new(0, 0, size.Width as u32, size.Height as u32)?;
    let mut board = Board::new();
    board
        .apply(Command::Begin {
            id: 1,
            brush: Brush {
                color: [255, 0, 0, 255],
                width: 48.0,
            },
            point: Point {
                x: 100.0,
                y: 100.0,
                pressure: 0.5,
            },
        })
        .unwrap();
    board
        .apply(Command::Append {
            id: 1,
            point: Point {
                x: 260.0,
                y: 100.0,
                pressure: 0.5,
            },
        })
        .unwrap();
    board.apply(Command::End { id: 1 }).unwrap();
    overlay.present(&board)?;
    let (tx, rx) = mpsc::sync_channel(1);
    let token = capture
        .pool
        .FrameArrived(&TypedEventHandler::new(move |_, _| {
            let _ = tx.try_send(());
            Ok(())
        }))?;
    capture.session.StartCapture()?;
    let saw_red = wait_for_pixel(&mut capture, &rx, true)?;
    println!("Ordinary capture includes annotation: {saw_red}");
    let iteration = capture.exclude_windows(&[overlay.hwnd()])?;
    let excluded = wait_for_pixel(&mut capture, &rx, false)?;
    let physical_red = unsafe {
        let dc = GetDC(None);
        let rgb = GetPixel(dc, 180, 100).0;
        ReleaseDC(None, dc);
        rgb & 255 > 240 && (rgb >> 8) & 255 < 15 && (rgb >> 16) & 255 < 15
    };
    capture.pool.RemoveFrameArrived(token)?;
    println!("Our capture excludes annotation: {excluded} (configuration {iteration})");
    println!("Desktop still shows annotation: {physical_red}");
    assert!(
        saw_red && excluded && physical_red,
        "Capture exclusion pixel acceptance failed"
    );
    println!("PASS. Zoom receiver and real Pencil latency are still separate device checks.");
    Ok(())
}

fn wait_for_pixel(
    capture: &mut MonitorCapture,
    rx: &mpsc::Receiver<()>,
    expected_red: bool,
) -> Result<bool> {
    let deadline = Instant::now() + Duration::from_secs(5);
    while Instant::now() < deadline {
        unsafe {
            let mut message = MSG::default();
            while PeekMessageW(&mut message, None, 0, 0, PM_REMOVE).as_bool() {
                let _ = TranslateMessage(&message);
                DispatchMessageW(&message);
            }
        }
        if rx.recv_timeout(Duration::from_millis(20)).is_err() {
            continue;
        }
        let Some(frame) = capture.latest_frame()? else {
            continue;
        };
        let at = (100 * frame.width as usize + 180) * 4;
        let pixel = &frame.bgra[at..at + 4];
        let red = pixel[2] > 240 && pixel[1] < 15 && pixel[0] < 15;
        if red == expected_red {
            return Ok(true);
        }
    }
    Ok(false)
}
