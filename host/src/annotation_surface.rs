//! Ink presentation has its own window/thread, independent of the settings UI.
//! eframe throttles minimized controllers to 10Hz. Only the newest board is
//! sampled here; input commands remain ordered in AnnotationState.
use crate::{
    annotation::AnnotationState,
    capture::{timing::FrameTimer, OutputInfo},
};
use deskink_windows::overlay::Overlay;
use parking_lot::Mutex;
use std::{
    io,
    sync::{
        atomic::{AtomicBool, Ordering},
        mpsc, Arc,
    },
    thread::JoinHandle,
    time::{Duration, Instant},
};
use windows::Win32::UI::WindowsAndMessaging::{DispatchMessageW, PeekMessageW, MSG, PM_REMOVE};

pub struct AnnotationSurface {
    pub state: Arc<AnnotationState>,
    running: Arc<AtomicBool>,
    wake: mpsc::SyncSender<()>,
    error: Arc<Mutex<Option<String>>>,
    worker: Option<JoinHandle<()>>,
}

impl AnnotationSurface {
    pub fn new(output: OutputInfo) -> io::Result<Self> {
        let (ready_tx, ready_rx) = mpsc::sync_channel(1);
        let (wake, events) = mpsc::sync_channel(1);
        let running = Arc::new(AtomicBool::new(true));
        let error = Arc::new(Mutex::new(None));
        let worker_running = running.clone();
        let worker_error = error.clone();
        let worker_wake = wake.clone();
        let worker = std::thread::Builder::new()
            .name("deskink-ink".into())
            .spawn(move || {
                // A HWND and its message pump stay on their creating thread.
                let overlay =
                    match Overlay::new(output.left, output.top, output.width, output.height) {
                        Ok(overlay) => overlay,
                        Err(error) => {
                            let _ = ready_tx.send(Err(io::Error::other(error.to_string())));
                            return;
                        }
                    };
                let state = Arc::new(AnnotationState::new(output, overlay.hwnd().0 as usize));
                state.attach_renderer(worker_wake);
                if ready_tx.send(Ok(state.clone())).is_err() {
                    return;
                }
                let mut shown = u64::MAX;
                let mut timer = FrameTimer::new();
                let mut next_frame = Instant::now();
                while worker_running.load(Ordering::Acquire) {
                    unsafe {
                        let mut message = MSG::default();
                        while PeekMessageW(&mut message, None, 0, 0, PM_REMOVE).as_bool() {
                            DispatchMessageW(&message);
                        }
                    }
                    timer.wait_until(next_frame);
                    if !worker_running.load(Ordering::Acquire) {
                        break;
                    }
                    let (revision, board, temporary) = {
                        let mut ink = state.ink.lock();
                        if ink.advance_time(crate::clock::host_now_us() / 1000) {
                            state.revision.fetch_add(1, Ordering::Release);
                        }
                        let revision = state.revision.load(Ordering::Acquire);
                        (
                            revision,
                            (revision != shown).then(|| ink.board().snapshot_for_display()),
                            ink.board().has_temporary_content(),
                        )
                    };
                    if let Some(board) = board {
                        next_frame = Instant::now() + Duration::from_micros(16_667);
                        // Never hold the input lock while rasterizing or presenting.
                        if let Err(error) = overlay.present(&board) {
                            *worker_error.lock() = Some(error.to_string());
                        }
                        shown = revision;
                    }
                    // At most one wake is retained. A sleeping/slow renderer must
                    // not build a queue of obsolete board snapshots.
                    let wait = if temporary {
                        Duration::from_millis(16)
                    } else {
                        Duration::from_millis(250)
                    };
                    let _ = events.recv_timeout(wait);
                }
            })?;
        match ready_rx
            .recv()
            .map_err(|_| io::Error::other("Ink renderer stopped before creating its window"))?
        {
            Ok(state) => Ok(Self {
                state,
                running,
                wake,
                error,
                worker: Some(worker),
            }),
            Err(error) => {
                let _ = worker.join();
                Err(error)
            }
        }
    }

    pub fn take_error(&self) -> Option<String> {
        self.error.lock().take()
    }
}

impl Drop for AnnotationSurface {
    fn drop(&mut self) {
        self.running.store(false, Ordering::Release);
        let _ = self.wake.try_send(());
        if let Some(worker) = self.worker.take() {
            let _ = worker.join();
        }
    }
}
