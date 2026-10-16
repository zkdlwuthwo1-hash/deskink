use super::{heartbeat, FrameSlot, RawFrame};
use crate::{control::SharedControl, stats::PIPELINE_STATS};
use deskink_windows::capture::MonitorCapture;
use std::{
    sync::{atomic::Ordering, mpsc, Arc},
    time::{Duration, Instant},
};
use windows::{
    Foundation::TypedEventHandler,
    Win32::{
        Foundation::{HWND, POINT},
        Graphics::Gdi::{MonitorFromPoint, MONITOR_DEFAULTTONULL},
        System::WinRT::{RoInitialize, RoUninitialize, RO_INIT_MULTITHREADED},
    },
};

pub fn run(
    slot: &FrameSlot,
    shared: SharedControl,
    generation: u64,
) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
    unsafe {
        RoInitialize(RO_INIT_MULTITHREADED)?;
    }
    struct Apartment;
    impl Drop for Apartment {
        fn drop(&mut self) {
            unsafe {
                RoUninitialize();
            }
        }
    }
    let _apartment = Apartment;
    let annotations = shared.annotation.as_ref().ok_or("DeskInk state missing")?;
    let output = &annotations.output;
    let monitor = unsafe {
        MonitorFromPoint(
            POINT {
                x: output.left + 1,
                y: output.top + 1,
            },
            MONITOR_DEFAULTTONULL,
        )
    };
    let mut capture = MonitorCapture::for_monitor(monitor)?;
    let size = capture.item.Size()?;
    if size.Width as u32 != output.width || size.Height as u32 != output.height {
        return Err("Display geometry changed; restart DeskInk for the new monitor size".into());
    }
    // Must succeed BEFORE capture starts. Never silently switch to a feed with
    // annotations already composited into it.
    capture.exclude_windows(&[HWND(annotations.overlay_handle as *mut _)])?;
    let (tx, rx) = mpsc::sync_channel(1);
    let token = capture
        .pool
        .FrameArrived(&TypedEventHandler::new(move |_, _| {
            let _ = tx.try_send(());
            Ok(())
        }))?;
    {
        let mut stats = PIPELINE_STATS.lock();
        stats.capture_resolution = (output.width, output.height);
        stats.set_capture_display(format!("{} / WGC", output.device_name));
    }
    capture.session.StartCapture()?;
    let mut count = 0;
    let mut last: Option<RawFrame> = None;
    let mut pending = false;
    let mut published = Instant::now() - Duration::from_secs(1);
    let mut next_frame = Instant::now();
    let mut timer = super::timing::FrameTimer::new();
    while shared.running.load(Ordering::SeqCst)
        && crate::supervisor::generation_is_current(generation)
    {
        heartbeat(&shared.hb_capture_loop_ms);
        let budget = super::frame_budget_for(shared.effective_fps());
        let arrived = if pending && shared.client_connected() {
            // recv_timeout uses a coarse OS wait on Windows. Repeated short
            // waits plus readback time capped a 60 Hz stream near 40 fps.
            // Use the same precise absolute-deadline timer as synthetic capture.
            timer.wait_until(next_frame.min(Instant::now() + budget));
            rx.try_recv().is_ok()
        } else {
            rx.recv_timeout(Duration::from_millis(16)).is_ok()
        };
        let connected = shared.client_connected();
        if arrived {
            if let Some(frame) = capture.latest_frame()? {
                if frame.width != output.width || frame.height != output.height {
                    return Err("Display size changed during capture".into());
                }
                // Drain the capture pool even while idle; retain only the latest frame.
                last = Some(RawFrame {
                    frame_number: count,
                    timestamp: Instant::now(),
                    data: Arc::new(frame.bgra),
                    width: frame.width,
                    height: frame.height,
                });
                pending = true;
            }
        }
        let due = Instant::now() >= next_frame;
        let keepalive = published.elapsed() >= Duration::from_millis(500);
        if connected
            && due
            && (pending || keepalive || shared.force_next_idr.load(Ordering::SeqCst))
        {
            if let Some(frame) = &last {
                let mut frame = frame.clone();
                if !pending {
                    // This is a new sample of an unchanged desktop. Reusing
                    // the previous sample's timestamp makes idle keepalives
                    // look hundreds of ms late and falsely triggers ABR.
                    frame.timestamp = Instant::now();
                }
                frame.frame_number = count;
                count += 1;
                heartbeat(&shared.hb_capture_frame_ms);
                PIPELINE_STATS
                    .lock()
                    .record_capture(frame.width, frame.height);
                slot.publish(frame);
                pending = false;
                published = Instant::now();
                next_frame += budget;
                // Never catch up by bursting stale frames after idle or stalls.
                if next_frame <= published {
                    next_frame = published + budget;
                }
            }
        }
    }
    capture.pool.RemoveFrameArrived(token)?;
    Ok(())
}
