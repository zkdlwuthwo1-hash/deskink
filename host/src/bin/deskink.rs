//! DeskInk's entry point: no virtual-display changes, remote OS input, audio,
//! autostart registration or upstream auto-updater.
use deskink_windows::overlay::Overlay;
use eframe::egui;
use eternal_host::{
    annotation::AnnotationState,
    capture,
    control::{SharedControl, SupervisorCommand},
    gpu::GpuInfo,
    stats::PIPELINE_STATS,
};
use std::{
    sync::{atomic::Ordering, mpsc, Arc},
    time::Duration,
};

fn main() -> Result<(), Box<dyn std::error::Error>> {
    unsafe {
        let _ = windows::Win32::UI::HiDpi::SetProcessDpiAwarenessContext(
            windows::Win32::UI::HiDpi::DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2,
        );
    }
    eternal_host::clock::init();
    tracing_subscriber::fmt().with_env_filter("info").init();
    ffmpeg_next::init()?;
    let outputs = capture::enumerate_outputs();
    if std::env::args().any(|arg| arg == "--list-monitors") {
        for (index, output) in outputs.iter().enumerate() {
            println!(
                "{index}: {} {}x{} at {},{}",
                output.device_name, output.width, output.height, output.left, output.top
            );
        }
        return Ok(());
    }
    let args: Vec<String> = std::env::args().collect();
    let selected = args
        .iter()
        .position(|arg| arg == "--monitor")
        .map(|at| {
            args.get(at + 1)
                .ok_or("--monitor requires an index")?
                .parse::<usize>()
                .map_err(|_| "Invalid monitor index")
        })
        .transpose()?
        .unwrap_or_else(|| {
            outputs
                .iter()
                .position(|output| output.is_primary)
                .unwrap_or(0)
        });
    let output = outputs
        .get(selected)
        .ok_or("Selected monitor is unavailable")?
        .clone();
    let overlay = Overlay::new(output.left, output.top, output.width, output.height)?;
    let annotation = Arc::new(AnnotationState::new(output, overlay.hwnd().0 as usize));
    let mut shared = SharedControl::new(9876, 15_000_000);
    shared.annotation = Some(annotation.clone());
    shared.audio_enabled.store(false, Ordering::SeqCst);
    shared.pairing.lock().require_usb_code = true;
    // Pairing is required on USB too. Token currently belongs to this run;
    // per-PC durable pairing is part of the later persistence task.
    let gpu = GpuInfo::detect();
    let (tx, rx) = mpsc::channel();
    let worker_shared = shared.clone();
    let worker_tx = tx.clone();
    let worker = std::thread::spawn(move || {
        eternal_host::supervisor::run(9876, worker_shared, gpu, worker_tx, rx)
    });
    let controller = shared.clone();
    let result = eframe::run_native(
        "DeskInk — USB first-stroke build",
        eframe::NativeOptions {
            viewport: egui::ViewportBuilder::default().with_inner_size([520.0, 330.0]),
            ..Default::default()
        },
        Box::new(move |cc| {
            cc.egui_ctx.set_visuals(egui::Visuals::light());
            *annotation.repaint.lock() = Some(cc.egui_ctx.clone());
            Ok(Box::new(DeskInk {
                shared: controller,
                annotation,
                overlay,
                shown_revision: 0,
                error: None,
            }))
        }),
    );
    shared.stop();
    let _ = tx.send(SupervisorCommand::Shutdown);
    let (done_tx, done_rx) = mpsc::channel();
    std::thread::spawn(move || {
        let _ = worker.join();
        let _ = done_tx.send(());
    });
    let _ = done_rx.recv_timeout(Duration::from_secs(3));
    result.map_err(|error| error.to_string().into())
}

struct DeskInk {
    shared: SharedControl,
    annotation: Arc<AnnotationState>,
    overlay: Overlay,
    shown_revision: u64,
    error: Option<String>,
}

impl eframe::App for DeskInk {
    fn ui(&mut self, ui: &mut egui::Ui, _frame: &mut eframe::Frame) {
        ui.painter()
            .rect_filled(ui.max_rect(), 0.0, egui::Color32::from_rgb(248, 249, 251));
        ui.visuals_mut().override_text_color = Some(egui::Color32::from_rgb(25, 31, 42));
        ui.spacing_mut().item_spacing.y = 9.0;
        let revision = self.annotation.revision.load(Ordering::Acquire);
        if revision != self.shown_revision {
            if let Err(error) = self.overlay.present(self.annotation.ink.lock().board()) {
                self.error = Some(error.to_string());
            }
            self.shown_revision = revision;
        }
        ui.heading("DeskInk");
        ui.label("USB first-stroke development build");
        ui.add_space(12.0);
        ui.label(format!(
            "Monitor: {} ({} x {})",
            self.annotation.output.device_name,
            self.annotation.output.width,
            self.annotation.output.height
        ));
        ui.label(if self.shared.client_connected() {
            "iPad connected"
        } else {
            "Connect your iPad by USB and open DeskInk"
        });
        ui.label(format!(
            "Pairing code: {:06}",
            self.shared.pairing.lock().code()
        ));
        let stats = PIPELINE_STATS.lock();
        ui.label(format!(
            "Capture {:.0} fps   Encode {:.0} fps",
            stats.capture_fps, stats.encode_fps
        ));
        ui.label(format!("Stream: {}", stats.codec_name));
        drop(stats);
        ui.add_space(12.0);
        ui.label("Screen capture and a basic pen are wired. Tool modes, saved boards and private zoom are subsequent tasks.");
        if let Some(error) = &self.error {
            ui.colored_label(egui::Color32::LIGHT_RED, error);
        }
        ui.ctx().request_repaint_after(Duration::from_secs(1));
    }
}
