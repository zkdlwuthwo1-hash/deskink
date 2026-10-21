//! DeskInk's entry point: no virtual-display changes, remote OS input, audio,
//! autostart registration or upstream auto-updater.
#![cfg_attr(all(windows, not(test)), windows_subsystem = "windows")]
use deskink_windows::monitor_control::{request_monitor, MonitorControl};
#[cfg(test)]
use deskink_windows::overlay::Overlay;
use eframe::egui;
use eternal_host::{
    annotation::AnnotationWorkspace,
    annotation_surface::AnnotationSurface,
    capture,
    control::{SharedControl, SupervisorCommand},
    gpu::GpuInfo,
    stats::PIPELINE_STATS,
};
use std::{
    collections::HashMap,
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
    if request_monitor(selected)? {
        return Ok(());
    }
    if args.iter().any(|arg| arg == "--switch-only") {
        return Err("No updated DeskInk is running. Close the old version once, then open a screen shortcut again.".into());
    }
    let output = outputs
        .get(selected)
        .ok_or("Selected monitor is unavailable")?
        .clone();
    let overlay = AnnotationSurface::new(output)?;
    let annotation = overlay.state.clone();
    let mut shared = SharedControl::new(9876, 15_000_000);
    let lan = eternal_host::lan::LanPanel::new(
        std::path::PathBuf::from(
            std::env::var_os("LOCALAPPDATA")
                .ok_or("Windows local app data folder is unavailable")?,
        )
        .join("DeskInk")
        .join("lan-setting.txt"),
    );
    shared.lan_enabled.store(lan.enabled(), Ordering::SeqCst);
    shared.annotation = Some(Arc::new(AnnotationWorkspace::new(annotation.clone())));
    shared.audio_enabled.store(false, Ordering::SeqCst);
    let pairing_path = std::path::PathBuf::from(
        std::env::var_os("LOCALAPPDATA").ok_or("Windows local app data folder is unavailable")?,
    )
    .join("DeskInk")
    .join("paired-tablets.bin");
    *shared.pairing.lock() = eternal_host::pairing::Pairing::remembered(&pairing_path)?;
    let gpu = GpuInfo::detect();
    let (tx, rx) = mpsc::channel();
    let worker_shared = shared.clone();
    let worker_tx = tx.clone();
    let worker = std::thread::spawn(move || {
        eternal_host::supervisor::run(9876, worker_shared, gpu, worker_tx, rx)
    });
    let controller = shared.clone();
    let command_tx = tx.clone();
    let result = eframe::run_native(
        "DeskInk",
        eframe::NativeOptions {
            viewport: egui::ViewportBuilder::default().with_inner_size([580.0, 600.0]),
            ..Default::default()
        },
        Box::new(move |cc| {
            cc.egui_ctx.set_visuals(egui::Visuals::light());
            *annotation.repaint.lock() = Some(cc.egui_ctx.clone());
            let (selection_tx, selection_rx) = mpsc::channel();
            let repaint = cc.egui_ctx.clone();
            let monitor_control = MonitorControl::new(move |index| {
                let _ = selection_tx.send(index);
                repaint.request_repaint();
            })?;
            Ok(Box::new(DeskInk {
                lan,
                updates: eternal_host::deskink_updates::UpdatePanel::start(&cc.egui_ctx),
                shared: controller,
                overlay,
                error: None,
                outputs,
                other_monitors: HashMap::new(),
                supervisor_tx: command_tx,
                selection_rx,
                _monitor_control: Some(monitor_control),
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
    lan: eternal_host::lan::LanPanel,
    updates: eternal_host::deskink_updates::UpdatePanel,
    shared: SharedControl,
    overlay: AnnotationSurface,
    error: Option<String>,
    outputs: Vec<capture::OutputInfo>,
    other_monitors: HashMap<String, AnnotationSurface>,
    supervisor_tx: mpsc::Sender<SupervisorCommand>,
    selection_rx: mpsc::Receiver<usize>,
    _monitor_control: Option<MonitorControl>,
}

impl DeskInk {
    fn select_monitor(&mut self, index: usize) -> Result<(), Box<dyn std::error::Error>> {
        let requested = self.outputs.get(index).ok_or("Monitor is unavailable")?;
        if requested.device_name == self.overlay.state.output.device_name {
            return Ok(());
        }
        if self
            .shared
            .session
            .lock()
            .client_info()
            .is_some_and(|client| {
                client.feature_caps & eternal_wire::v2::control::FEATURE_MONITOR_BOARDS == 0
            })
        {
            return Err(
                "Update the iPad DeskInk app to build 11 to keep separate ink for each screen."
                    .into(),
            );
        }
        let output = capture::enumerate_outputs()
            .into_iter()
            .find(|output| output.device_name == requested.device_name)
            .ok_or("Monitor was disconnected; the current screen is unchanged")?;
        if (output.width, output.height, output.left, output.top)
            != (
                requested.width,
                requested.height,
                requested.left,
                requested.top,
            )
        {
            return Err(
                "Display arrangement changed. Close and reopen DeskInk after saving any ink."
                    .into(),
            );
        }
        let next = if let Some(surface) = self.other_monitors.remove(&output.device_name) {
            surface
        } else {
            let overlay = AnnotationSurface::new(output)?;
            *overlay.state.repaint.lock() = self.overlay.state.repaint.lock().clone();
            overlay
        };
        let workspace = self
            .shared
            .annotation
            .as_ref()
            .ok_or("DeskInk workspace is unavailable")?;
        workspace.select(next.state.clone());
        if self.supervisor_tx.send(SupervisorCommand::Restart).is_err() {
            workspace.select(self.overlay.state.clone());
            self.other_monitors
                .insert(next.state.output.device_name.clone(), next);
            return Err("The streaming worker has stopped; close and reopen DeskInk".into());
        }
        let old_name = self.overlay.state.output.device_name.clone();
        let old = std::mem::replace(&mut self.overlay, next);
        self.other_monitors.insert(old_name, old);
        tracing::info!(monitor = index, display = %self.overlay.state.output.device_name, "DeskInk monitor selected");
        self.error = None;
        Ok(())
    }
}

impl eframe::App for DeskInk {
    fn logic(&mut self, ctx: &egui::Context, _frame: &mut eframe::Frame) {
        // Rapid Stream Deck taps collapse to the last requested screen.
        if let Some(index) = self.selection_rx.try_iter().last() {
            if let Err(error) = self.select_monitor(index) {
                self.error = Some(error.to_string());
            }
        }
        // Ink and temporary-content expiry run on their owning presentation
        // threads, even while eframe throttles this minimized settings window.
        for surface in self.other_monitors.values() {
            if let Some(error) = surface.take_error() {
                self.error = Some(error);
            }
        }
        if let Some(error) = self.overlay.take_error() {
            self.error = Some(error);
        }
        ctx.request_repaint_after(Duration::from_millis(250));
    }

    fn ui(&mut self, ui: &mut egui::Ui, _frame: &mut eframe::Frame) {
        ui.painter()
            .rect_filled(ui.max_rect(), 0.0, egui::Color32::from_rgb(248, 249, 251));
        ui.visuals_mut().override_text_color = Some(egui::Color32::from_rgb(25, 31, 42));
        ui.spacing_mut().item_spacing.y = 9.0;
        egui::ScrollArea::vertical().show(ui, |ui| {
            ui.heading("DeskInk");
            ui.label("Write on Windows from iPad or Android tablet");
            ui.add_space(12.0);
            let mut selected = None;
            ui.horizontal_wrapped(|ui| {
                for (index, output) in self.outputs.iter().enumerate() {
                    let active = output.device_name == self.overlay.state.output.device_name;
                    if ui
                        .selectable_label(active, format!("Screen {}", index + 1))
                        .clicked()
                    {
                        selected = Some(index);
                    }
                }
            });
            if let Some(index) = selected {
                if let Err(error) = self.select_monitor(index) {
                    self.error = Some(error.to_string());
                }
            }
            ui.label(format!(
                "Monitor: {} ({} x {})",
                self.overlay.state.output.device_name,
                self.overlay.state.output.width,
                self.overlay.state.output.height
            ));
            ui.label(if self.shared.client_connected() {
                "Tablet connected"
            } else if self.shared.lan_enabled.load(Ordering::SeqCst) {
                "Connect by USB or use the Wi-Fi / LAN address below"
            } else {
                "Connect your tablet by USB and open DeskInk"
            });
            ui.label(format!(
                "Pairing code: {:06}",
                self.shared.pairing.lock().code()
            ));
            ui.small("Enter the code once per PC. Registered tablets reconnect automatically.");
            self.lan.show(ui, &self.shared, &self.supervisor_tx);
            let remembered = self.shared.pairing.lock().remembered_count();
            if remembered > 0
                && ui
                    .button(format!("Forget paired tablets ({remembered})"))
                    .clicked()
            {
                if let Err(error) = self.shared.pairing.lock().regenerate_token() {
                    self.error = Some(error.to_string());
                }
            }
            let stats = PIPELINE_STATS.lock();
            ui.label(format!(
                "Capture {:.0} fps   Encode {:.0} fps",
                stats.capture_fps, stats.encode_fps
            ));
            ui.label(format!("Stream: {}", stats.codec_name));
            drop(stats);
            ui.add_space(12.0);
            ui.label("Choose your pen, eraser, laser and colors on your tablet.");
            ui.label("Screen shortcuts switch here. Each screen keeps its own ink.");
            ui.label("Closing this app clears ink on all screens.");
            let monitor = self.outputs.iter().position(|output| output.device_name == self.overlay.state.output.device_name).unwrap_or(0);
            if self.updates.show(ui, monitor) {
                ui.ctx().send_viewport_cmd(egui::ViewportCommand::Close);
            }
            if let Some(error) = &self.error {
                ui.colored_label(egui::Color32::LIGHT_RED, error);
            }
        });
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use deskink_core::{Board, Brush, Command, Point};
    use eframe::App;

    #[test]
    #[ignore = "interactive desktop diagnostic; briefly shows a 64px synthetic ink marker"]
    fn real_controller_window_keeps_ink_fresh_when_minimized() {
        use std::{
            sync::{atomic::AtomicBool, Mutex},
            time::Instant,
        };
        use windows::Win32::Graphics::Gdi::{GetDC, GetPixel, ReleaseDC};
        use winit::platform::windows::EventLoopBuilderExtWindows;

        struct Probe {
            app: DeskInk,
            start: Instant,
            minimized: bool,
        }
        impl eframe::App for Probe {
            fn logic(&mut self, ctx: &egui::Context, frame: &mut eframe::Frame) {
                self.app.logic(ctx, frame);
                if !self.minimized && self.start.elapsed() >= Duration::from_secs(3) {
                    self.minimized = true;
                    ctx.send_viewport_cmd(egui::ViewportCommand::Minimized(true));
                }
                if self.start.elapsed() >= Duration::from_secs(6) {
                    ctx.send_viewport_cmd(egui::ViewportCommand::Close);
                }
            }
            fn ui(&mut self, ui: &mut egui::Ui, frame: &mut eframe::Frame) {
                self.app.ui(ui, frame);
            }
        }

        let timings = Arc::new(Mutex::new([Vec::<f64>::new(), Vec::new()]));
        let times = Arc::new(Mutex::new(vec![None::<Instant>; 400]));
        let stop = Arc::new(AtomicBool::new(false));
        let handles = Arc::new(Mutex::new(Vec::new()));
        let task_timings = timings.clone();
        let task_stop = stop.clone();
        let task_handles = handles.clone();
        let result = eframe::run_native(
            "DeskInk controller timing diagnostic",
            eframe::NativeOptions {
                viewport: egui::ViewportBuilder::default()
                    .with_inner_size([520.0, 420.0])
                    .with_position([600.0, 300.0]),
                event_loop_builder: Some(Box::new(|builder| {
                    builder.with_any_thread(true);
                })),
                ..Default::default()
            },
            Box::new(move |cc| {
                eternal_host::clock::init();
                let start = Instant::now();
                let output = capture::OutputInfo {
                    adapter_index: 0,
                    output_index: 0,
                    device_name: "diagnostic".into(),
                    width: 64,
                    height: 64,
                    left: 0,
                    top: 0,
                    is_primary: true,
                    adapter_name: "synthetic".into(),
                };
                let overlay = AnnotationSurface::new(output).unwrap();
                let annotation = overlay.state.clone();
                *annotation.repaint.lock() = Some(cc.egui_ctx.clone());
                let app = DeskInk {
                    lan: eternal_host::lan::LanPanel::new(std::path::PathBuf::new()),
                    updates: eternal_host::deskink_updates::UpdatePanel::disabled(),
                    shared: SharedControl::new(0, 15_000_000),
                    overlay,
                    error: None,
                    outputs: Vec::new(),
                    other_monitors: HashMap::new(),
                    supervisor_tx: mpsc::channel().0,
                    selection_rx: mpsc::channel().1,
                    _monitor_control: None,
                };
                let writer_times = times.clone();
                let writer_stop = task_stop.clone();
                let writer = std::thread::spawn(move || {
                    let peer = PeerId::usb(99);
                    let mut sequence = 0;
                    for sample in 1u64..=359 {
                        let deadline = start + Duration::from_micros(sample * 16_667);
                        if let Some(delay) = deadline.checked_duration_since(Instant::now()) {
                            std::thread::sleep(delay);
                        }
                        if writer_stop.load(Ordering::Relaxed) {
                            break;
                        }
                        writer_times.lock().unwrap()[sample as usize] = Some(Instant::now());
                        for command in [
                            serde_json::json!({"type":"clear"}),
                            serde_json::json!({"type":"begin","id":sample,"brush":{"color":[sample & 255,sample >> 8,120,255],"width":20},"point":{"x":32,"y":32,"pressure":0.5}}),
                            serde_json::json!({"type":"end","id":sample}),
                        ] {
                            sequence += 1;
                            let bytes = serde_json::to_vec(&serde_json::json!({"version":1,"session":42,"epoch":3,"seq":sequence,"command":command})).unwrap();
                            assert!(annotation.receive(peer, Some(peer), Some(42), 3, &bytes));
                        }
                    }
                });
                let observer = std::thread::spawn(move || {
                    while !task_stop.load(Ordering::Relaxed)
                        && start.elapsed() < Duration::from_secs(7)
                    {
                        let elapsed = start.elapsed().as_secs_f64();
                        let phase = if (1.0..2.8).contains(&elapsed) {
                            Some(0)
                        } else if (3.5..5.8).contains(&elapsed) {
                            Some(1)
                        } else {
                            None
                        };
                        if let Some(phase) = phase {
                            let pixel = unsafe {
                                let dc = GetDC(None);
                                let pixel = GetPixel(dc, 32, 32).0;
                                ReleaseDC(None, dc);
                                pixel
                            };
                            if (pixel >> 16) & 255 == 120 {
                                let sample = (pixel & 65535) as usize;
                                if let Some(Some(sent)) = times.lock().unwrap().get(sample) {
                                    task_timings.lock().unwrap()[phase]
                                        .push(sent.elapsed().as_secs_f64() * 1000.0);
                                }
                            }
                        }
                        std::thread::sleep(Duration::from_millis(2));
                    }
                });
                task_handles.lock().unwrap().extend([writer, observer]);
                Ok(Box::new(Probe {
                    app,
                    start,
                    minimized: false,
                }))
            }),
        );
        stop.store(true, Ordering::Relaxed);
        for handle in handles.lock().unwrap().drain(..) {
            handle.join().unwrap();
        }
        result.unwrap();
        let mut measurements = timings.lock().unwrap();
        for (phase, values) in ["visible", "minimized"].iter().zip(measurements.iter_mut()) {
            assert!(
                values.len() >= 20,
                "No usable desktop ink pixels for {phase}"
            );
            values.sort_by(f64::total_cmp);
            let p95 = values[values.len() * 95 / 100];
            println!(
                "Desktop ink freshness {phase}: p50={:.1}ms p95={p95:.1}ms max={:.1}ms above_50ms={} samples={}",
                values[values.len() / 2],
                values[values.len() - 1],
                values.iter().filter(|&&age| age > 50.0).count(),
                values.len()
            );
            assert!(
                p95 < 50.0,
                "The controller window must not hold desktop ink back: {phase} p95={p95:.1}ms"
            );
        }
    }
    use eternal_host::transport::link::PeerId;
    use windows::Win32::Graphics::Gdi::{GetDC, GetPixel, ReleaseDC};

    #[test]
    #[ignore = "interactive Release diagnostic: temporary 1080p ink for three seconds"]
    fn pen_input_remains_responsive_while_the_desktop_is_presented() {
        use std::time::Instant;
        eternal_host::clock::init();
        let output = capture::enumerate_outputs().remove(0);
        let overlay = AnnotationSurface::new(output).unwrap();
        let annotation = overlay.state.clone();
        let peer = PeerId::usb(99);
        let mut sequence = 0;
        let mut send = |command| {
            sequence += 1;
            let bytes = serde_json::to_vec(&serde_json::json!({"version":1,"session":42,"epoch":3,"seq":sequence,"command":command})).unwrap();
            assert!(annotation.receive(peer, Some(peer), Some(42), 3, &bytes));
        };
        for id in 1..=100 {
            send(
                serde_json::json!({"type":"begin","id":id,"brush":{"color":[255,130,30,255],"width":2},"point":{"x":40,"y":80+id%20*8,"pressure":0.5}}),
            );
            for point in 1..100 {
                send(
                    serde_json::json!({"type":"append","id":id,"point":{"x":40+point*4,"y":80+id%20*8+point%3,"pressure":0.5}}),
                );
            }
            send(serde_json::json!({"type":"end","id":id}));
        }
        send(
            serde_json::json!({"type":"begin","id":1001,"brush":{"color":[255,80,40,255],"width":2},"point":{"x":40,"y":300,"pressure":0.5}}),
        );
        let mut app = DeskInk {
            lan: eternal_host::lan::LanPanel::new(std::path::PathBuf::new()),
            updates: eternal_host::deskink_updates::UpdatePanel::disabled(),
            shared: SharedControl::new(0, 15_000_000),
            overlay,
            error: None,
            outputs: Vec::new(),
            other_monitors: HashMap::new(),
            supervisor_tx: mpsc::channel().0,
            selection_rx: mpsc::channel().1,
            _monitor_control: None,
        };
        let context = egui::Context::default();
        app.logic(&context, &mut eframe::Frame::_new_kittest());
        let start = Instant::now();
        let writer = std::thread::spawn(move || {
            let mut durations = Vec::new();
            for point in 1..=600 {
                sequence += 1;
                let bytes = serde_json::to_vec(&serde_json::json!({"version":1,"session":42,"epoch":3,"seq":sequence,
                    "command":{"type":"append","id":1001,"point":{"x":40+point%400,"y":300+point%5,"pressure":0.5}}})).unwrap();
                let began = Instant::now();
                assert!(annotation.receive(peer, Some(peer), Some(42), 3, &bytes));
                durations.push(began.elapsed().as_secs_f64() * 1000.0);
                let deadline = start + Duration::from_micros(point * 4167);
                if let Some(delay) = deadline.checked_duration_since(Instant::now()) {
                    std::thread::sleep(delay);
                }
            }
            durations
        });
        for frame in 1..=155 {
            app.logic(&context, &mut eframe::Frame::_new_kittest());
            let deadline = start + Duration::from_micros(frame * 16667);
            if let Some(delay) = deadline.checked_duration_since(Instant::now()) {
                std::thread::sleep(delay);
            }
        }
        let mut durations = writer.join().unwrap();
        durations.sort_by(f64::total_cmp);
        println!(
            "Concurrent pen receive: p50={:.3}ms p95={:.3}ms max={:.3}ms",
            durations[300], durations[570], durations[599]
        );
        assert!(
            durations[570] < 4.0,
            "Presenting the desktop must not block new pen input for a frame"
        );
        assert!(app.error.is_none());
    }

    #[test]
    #[ignore = "interactive two-monitor diagnostic; no updated DeskInk may be running"]
    fn shortcuts_switch_real_capture_and_keep_both_boards() {
        use eternal_host::transport::session::ConfigSource;
        use eternal_wire::v2::control::{ControlMessage, Hello2, StreamConfig};
        use std::{os::windows::process::CommandExt, time::Instant};
        use windows::Win32::UI::WindowsAndMessaging::{
            DispatchMessageW, PeekMessageW, MSG, PM_REMOVE,
        };
        assert!(
            !deskink_windows::monitor_control::is_running(),
            "Close the updated DeskInk before this isolated diagnostic"
        );
        eternal_host::clock::init();
        unsafe {
            let _ = windows::Win32::UI::HiDpi::SetProcessDpiAwarenessContext(
                windows::Win32::UI::HiDpi::DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2,
            );
        }
        let outputs = capture::enumerate_outputs();
        assert!(
            outputs.len() >= 2,
            "This diagnostic needs two connected monitors"
        );
        let colors = [[21, 181, 73, 255], [227, 41, 89, 255]];
        let mut markers = Vec::new();
        for (output, color) in outputs.iter().zip(colors) {
            let marker = Overlay::new(output.left, output.top, 64, 64).unwrap();
            let mut board = Board::new();
            board
                .apply(Command::Begin {
                    id: 99,
                    brush: Brush { color, width: 40.0 },
                    point: Point {
                        x: 32.0,
                        y: 32.0,
                        pressure: 0.5,
                    },
                })
                .unwrap();
            marker.present(&board).unwrap();
            markers.push(marker);
        }
        let output = outputs[0].clone();
        let overlay = AnnotationSurface::new(output).unwrap();
        let annotation = overlay.state.clone();
        let mut shared = SharedControl::new(0, 15_000_000);
        shared.annotation = Some(Arc::new(AnnotationWorkspace::new(annotation.clone())));
        struct ProbeConfig;
        impl ConfigSource for ProbeConfig {
            fn host_name(&self) -> String {
                "Isolated capture probe".into()
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
        shared.session.lock().handle_control(
            PeerId::usb(99),
            0,
            ControlMessage::Hello2(Hello2 {
                proto_min: 2,
                proto_max: 2,
                client_nonce: 1,
                listen_port: 0,
                decoder_caps: 1,
                feature_caps: eternal_wire::v2::control::FEATURE_MONITOR_BOARDS,
                screen_px_w: 1920,
                screen_px_h: 1080,
                screen_pt_w: 960,
                screen_pt_h: 540,
                refresh_hz: 60,
                device_name: "Local probe".into(),
                device_id: 1,
                preferred_fps: 60,
                auth_token: [0; 16],
                pairing_code: 0,
            }),
            &ProbeConfig,
            Instant::now(),
        );
        assert!(shared.client_connected());
        let (selection_tx, selection_rx) = mpsc::channel();
        let control = MonitorControl::new(move |index| {
            selection_tx.send(index).unwrap();
        })
        .unwrap();
        let (supervisor_tx, _supervisor_rx) = mpsc::channel();
        let mut app = DeskInk {
            lan: eternal_host::lan::LanPanel::new(std::path::PathBuf::new()),
            updates: eternal_host::deskink_updates::UpdatePanel::disabled(),
            shared,
            overlay,
            error: None,
            outputs: outputs.clone(),
            other_monitors: HashMap::new(),
            supervisor_tx,
            selection_rx,
            _monitor_control: Some(control),
        };
        let project = std::path::Path::new(env!("CARGO_MANIFEST_DIR"))
            .parent()
            .unwrap();
        for (step, index) in [0usize, 1, 0].into_iter().enumerate() {
            // This is the exact script used by both Desktop .lnk files.
            let result = std::process::Command::new("powershell.exe")
                .args(["-NoProfile", "-File"])
                .arg(project.join("scripts/start-deskink.ps1"))
                .arg("-Monitor")
                .arg(index.to_string())
                .creation_flags(0x08000000)
                .status()
                .unwrap();
            assert!(result.success());
            unsafe {
                let mut message = MSG::default();
                while PeekMessageW(&mut message, None, 0, 0, PM_REMOVE).as_bool() {
                    DispatchMessageW(&message);
                }
            }
            app.logic(
                &egui::Context::default(),
                &mut eframe::Frame::_new_kittest(),
            );
            assert!(app.error.is_none(), "{:?}", app.error);
            assert_eq!(
                app.overlay.state.output.device_name,
                outputs[index].device_name
            );
            if step < 2 {
                let bytes = serde_json::json!({"version":1,"session":42,"epoch":1,"seq":1,
                    "command":{"type":"begin","id":7,"brush":{"color":[255,80,40,255],"width":4},
                    "point":{"x":100+index*200,"y":200,"pressure":0.5}}});
                assert!(app.overlay.state.receive(
                    PeerId::usb(99),
                    Some(PeerId::usb(99)),
                    Some(42),
                    1,
                    &serde_json::to_vec(&bytes).unwrap()
                ));
            }
            assert_eq!(
                app.overlay.state.ink.lock().board().strokes()[0].points[0].x,
                if index == 0 { 100.0 } else { 300.0 }
            );
            let mut pipeline = app.shared.clone();
            pipeline.annotation = pipeline
                .annotation
                .as_ref()
                .map(|workspace| Arc::new(workspace.snapshot()));
            pipeline.running.store(true, Ordering::SeqCst);
            let generation = step as u64 + 1;
            eternal_host::supervisor::CURRENT_GENERATION.store(generation, Ordering::SeqCst);
            let (producer, consumer) = capture::frame_slot();
            let consumer = Arc::new(consumer);
            let read = consumer.clone();
            let (frame_tx, frame_rx) = mpsc::channel();
            std::thread::spawn(move || {
                let _ = frame_tx.send(read.blocking_take());
            });
            let capture_shared = pipeline.clone();
            let capture_thread = std::thread::spawn(move || {
                capture::run_capture_stage(producer, capture_shared, 0, generation)
                    .map_err(|error| error.to_string())
            });
            let frame = frame_rx.recv_timeout(Duration::from_secs(5));
            pipeline.stop();
            consumer.close();
            capture_thread.join().unwrap().unwrap();
            let frame = frame.unwrap().expect("Actual monitor frame");
            let offset = (32 * frame.width as usize + 32) * 4;
            let [r, g, b, _] = colors[index];
            assert_eq!(
                &frame.data[offset..offset + 3],
                &[b, g, r],
                "Capture must follow the selected physical monitor"
            );
            println!(
                "screen {}: shortcut delivered, own ink retained, real WGC pixels verified",
                index + 1
            );
        }
        assert_eq!(app.other_monitors.len(), 1);
        drop(markers);
    }

    #[test]
    fn accepted_ink_reaches_desktop_without_a_controller_ui_pass() {
        // No controller callbacks at all: input must reach real desktop pixels.
        unsafe {
            let _ = windows::Win32::UI::HiDpi::SetProcessDpiAwarenessContext(
                windows::Win32::UI::HiDpi::DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2,
            );
        }
        let output = capture::OutputInfo {
            adapter_index: 0,
            output_index: 0,
            device_name: "test".into(),
            width: 64,
            height: 64,
            left: 0,
            top: 0,
            is_primary: true,
            adapter_name: "test".into(),
        };
        let backdrop = Overlay::new(0, 0, 64, 64).unwrap();
        let mut initial = Board::new();
        initial
            .apply(Command::Begin {
                id: 1,
                brush: Brush {
                    color: [17, 197, 89, 255],
                    width: 20.0,
                },
                point: Point {
                    x: 32.0,
                    y: 32.0,
                    pressure: 0.5,
                },
            })
            .unwrap();
        backdrop.present(&initial).unwrap();
        let surface = AnnotationSurface::new(output).unwrap();
        let annotation = surface.state.clone();
        let expect_red = |expected: bool| {
            let deadline = std::time::Instant::now() + Duration::from_millis(250);
            loop {
                let pixel = unsafe {
                    let dc = GetDC(None);
                    let value = GetPixel(dc, 32, 32).0;
                    ReleaseDC(None, dc);
                    value & 0x00ff_ffff
                };
                if (pixel == (237 | (19 << 8) | (53 << 16))) == expected {
                    break;
                }
                assert!(
                    std::time::Instant::now() < deadline,
                    "Ink presentation did not update desktop pixels"
                );
                std::thread::sleep(Duration::from_millis(2));
            }
        };
        let peer = PeerId::usb(7);
        assert!(annotation.receive(peer, Some(peer), Some(42), 3,
            br#"{"version":1,"session":42,"epoch":3,"seq":1,"command":{"type":"begin","id":7,"brush":{"color":[237,19,53,255],"width":20},"point":{"x":32,"y":32,"pressure":0.4}}}"#));
        expect_red(true);
        let mut sequence = 1;
        for (command, red_visible) in [
            (serde_json::json!({"type":"end","id":7}), true),
            (
                serde_json::json!({"type":"erase_begin","id":8,"radius":12,"point":{"x":32,"y":32,"pressure":0.5}}),
                false,
            ),
            (serde_json::json!({"type":"erase_end","id":8}), false),
            (serde_json::json!({"type":"undo"}), true),
            (
                serde_json::json!({"type":"set_visible","visible":false}),
                false,
            ),
            (
                serde_json::json!({"type":"set_visible","visible":true}),
                true,
            ),
        ] {
            sequence += 1;
            let message = serde_json::json!({"version":1,"session":42,"epoch":3,"seq":sequence,"command":command});
            assert!(annotation.receive(
                peer,
                Some(peer),
                Some(42),
                3,
                &serde_json::to_vec(&message).unwrap()
            ));
            expect_red(red_visible);
        }
    }
}
