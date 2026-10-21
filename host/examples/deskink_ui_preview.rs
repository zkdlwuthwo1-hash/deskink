//! Native visual/interaction QA for the actual DeskInk renderer. No host,
//! capture, USB listener, annotation surface, updater, or real settings start.
use eframe::egui::{self, pos2, vec2, Event, Key, Modifiers, PointerButton, Rect};
use eternal_host::{
    control::{SharedControl, SupervisorCommand},
    deskink_ui::{self as design, Action, Connection, DashboardSnapshot, Monitor},
    deskink_updates::UpdatePanel,
    lan::LanPanel,
    transport::{link::PeerId, session::ConfigSource},
};
use eternal_wire::v2::control::{ControlMessage, Hello2, StreamConfig, CAP_DECODE_H264};
use std::{io::Write, path::PathBuf, sync::mpsc, time::Duration};

struct Preview {
    state: DashboardSnapshot,
    lan: LanPanel,
    shared: SharedControl,
    commands: mpsc::Sender<SupervisorCommand>,
    _receiver: mpsc::Receiver<SupervisorCommand>,
    updates: UpdatePanel,
    temporary: PathBuf,
    screenshot: Option<PathBuf>,
    capture_started: Option<std::time::Instant>,
    capture_requested: bool,
}

struct PreviewConfig;
impl ConfigSource for PreviewConfig {
    fn stream_config(&self) -> StreamConfig {
        StreamConfig {
            stream_epoch: 1,
            width: 2560,
            height: 1440,
            fps: 60,
            codec: 0,
            flags: 0,
            bitrate_bps: 15_000_000,
        }
    }
    fn host_name(&self) -> String {
        "UI preview".into()
    }
}

impl Preview {
    fn new(mode: &str, screenshot: Option<PathBuf>) -> Self {
        let temporary =
            std::env::temp_dir().join(format!("deskink-ui-preview-{}", std::process::id()));
        std::fs::create_dir_all(&temporary).unwrap();
        let lan_enabled = mode != "idle";
        let settings = temporary.join("lan-setting.txt");
        std::fs::write(&settings, if lan_enabled { "enabled" } else { "disabled" }).unwrap();
        let (commands, receiver) = mpsc::channel();
        let shared = SharedControl::new(0, 15_000_000);
        if mode == "usb" || mode == "wifi" {
            let peer = if mode == "usb" {
                PeerId::usb(1)
            } else {
                PeerId::udp("127.0.0.1:9876".parse().unwrap())
            };
            shared.session.lock().handle_control(
                peer,
                0,
                ControlMessage::Hello2(Hello2 {
                    proto_min: 2,
                    proto_max: 2,
                    client_nonce: 1,
                    listen_port: 9876,
                    decoder_caps: CAP_DECODE_H264,
                    feature_caps: 0,
                    screen_px_w: 2560,
                    screen_px_h: 1600,
                    screen_pt_w: 1280,
                    screen_pt_h: 800,
                    refresh_hz: 60,
                    device_name: "Preview tablet".into(),
                    device_id: 1,
                    preferred_fps: 60,
                    auth_token: [0; 16],
                    pairing_code: 0,
                }),
                &PreviewConfig,
                std::time::Instant::now(),
            );
            assert!(shared.client_connected());
        }
        Self {
            state: DashboardSnapshot {
                monitors: vec![
                    Monitor {
                        name: "Preview display 1".into(),
                        width: 2560,
                        height: 1440,
                        primary: true,
                        active: true,
                    },
                    Monitor {
                        name: "Preview display 2".into(),
                        width: 1920,
                        height: 1080,
                        primary: false,
                        active: false,
                    },
                ],
                connection: match mode {
                    "usb" => Some(Connection {
                        device_name: "회의용 iPad".into(),
                        usb: true,
                    }),
                    "wifi" => Some(Connection {
                        device_name: "Preview Galaxy Tab".into(),
                        usb: false,
                    }),
                    _ => None,
                },
                lan_enabled,
                pairing_code: 123456,
                remembered: 2,
                capture_fps: 60.0,
                encode_fps: 60.0,
                codec: "H.264".into(),
                error: (mode == "error")
                    .then(|| "Monitor was disconnected; the current screen is unchanged".into()),
            },
            lan: LanPanel::new(settings),
            shared,
            commands,
            _receiver: receiver,
            updates: UpdatePanel::disabled(),
            temporary,
            screenshot,
            capture_started: None,
            capture_requested: false,
        }
    }

    fn render(&mut self, ui: &mut egui::Ui) -> Option<Action> {
        let action = design::dashboard(
            ui,
            &self.state,
            |ui| self.lan.show(ui, &self.shared, &self.commands),
            |ui| {
                self.updates.show(ui, 0);
            },
        );
        match action {
            Some(Action::SelectMonitor(index)) => {
                for (at, monitor) in self.state.monitors.iter_mut().enumerate() {
                    monitor.active = at == index;
                }
            }
            Some(Action::ForgetTablets) => self.state.remembered = 0,
            None => {}
        }
        action
    }
}

impl Drop for Preview {
    fn drop(&mut self) {
        // Only remove the two known files this preview owns.
        let _ = std::fs::remove_file(self.temporary.join("lan-setting.txt"));
        let _ = std::fs::remove_dir(&self.temporary);
    }
}

impl eframe::App for Preview {
    fn logic(&mut self, ctx: &egui::Context, _: &mut eframe::Frame) {
        if let Some(path) = &self.screenshot {
            for event in ctx.input(|input| input.events.clone()) {
                if let Event::Screenshot { image, .. } = event {
                    let mut file = std::fs::File::create(path).unwrap();
                    write!(file, "P6\n{} {}\n255\n", image.size[0], image.size[1]).unwrap();
                    for pixel in &image.pixels {
                        file.write_all(&pixel.to_array()[..3]).unwrap();
                    }
                    println!(
                        "Captured {} x {} to {}",
                        image.size[0],
                        image.size[1],
                        path.display()
                    );
                    ctx.send_viewport_cmd(egui::ViewportCommand::Close);
                }
            }
        }
    }
    fn ui(&mut self, ui: &mut egui::Ui, _: &mut eframe::Frame) {
        self.render(ui);
        if self.screenshot.is_some() && !self.capture_requested {
            let start = self
                .capture_started
                .get_or_insert_with(std::time::Instant::now);
            if start.elapsed() >= Duration::from_millis(700) {
                self.capture_requested = true;
                ui.ctx()
                    .send_viewport_cmd(
                        egui::ViewportCommand::Screenshot(egui::UserData::default()),
                    );
            } else {
                ui.ctx().request_repaint_after(Duration::from_millis(80));
            }
        }
    }
}

fn frame(
    ctx: &egui::Context,
    app: &mut Preview,
    events: Vec<Event>,
    time: &mut f64,
) -> (Option<Action>, egui::FullOutput) {
    *time += 0.2;
    let mut action = None;
    let mut output = ctx.run_ui(
        egui::RawInput {
            screen_rect: Some(Rect::from_min_size(pos2(0.0, 0.0), vec2(640.0, 1100.0))),
            events,
            time: Some(*time),
            ..Default::default()
        },
        |ui| {
            action = app.render(ui);
        },
    );
    output.textures_delta.clear();
    (action, output)
}

fn text_position(output: &egui::FullOutput, label: &str) -> egui::Pos2 {
    fn find(shape: &egui::Shape, label: &str) -> Option<egui::Pos2> {
        match shape {
            egui::Shape::Text(text) if text.galley.text() == label => {
                Some(text.pos + text.galley.size() / 2.0)
            }
            egui::Shape::Vec(shapes) => shapes.iter().find_map(|shape| find(shape, label)),
            _ => None,
        }
    }
    output
        .shapes
        .iter()
        .find_map(|shape| find(&shape.shape, label))
        .unwrap_or_else(|| panic!("UI text missing: {label}"))
}

fn click(
    ctx: &egui::Context,
    app: &mut Preview,
    output: &egui::FullOutput,
    label: &str,
    time: &mut f64,
) -> (Option<Action>, egui::FullOutput) {
    let pos = text_position(output, label);
    frame(
        ctx,
        app,
        vec![
            Event::PointerMoved(pos),
            Event::PointerButton {
                pos,
                button: PointerButton::Primary,
                pressed: true,
                modifiers: Modifiers::NONE,
            },
        ],
        time,
    );
    frame(
        ctx,
        app,
        vec![Event::PointerButton {
            pos,
            button: PointerButton::Primary,
            pressed: false,
            modifiers: Modifiers::NONE,
        }],
        time,
    )
}

fn verify() {
    let ctx = egui::Context::default();
    design::install(&ctx);
    ctx.all_styles_mut(|style| style.animation_time = 0.0);
    let mut app = Preview::new("lan", None);
    let mut time = 0.0;
    frame(&ctx, &mut app, vec![], &mut time);
    let (_, output) = frame(&ctx, &mut app, vec![], &mut time);
    assert!(
        ctx.fonts_mut(|fonts| fonts.has_glyphs(&egui::FontId::proportional(12.5), "회의용 iPad")),
        "Windows device name glyphs must exist"
    );
    let (action, output) = click(&ctx, &mut app, &output, "Screen 2", &mut time);
    assert_eq!(action, Some(Action::SelectMonitor(1)));
    assert!(app.state.monitors[1].active);
    // Buttons gain keyboard focus through Tab navigation, not pointer clicks.
    for _ in 0..2 {
        for pressed in [true, false] {
            frame(
                &ctx,
                &mut app,
                vec![Event::Key {
                    key: Key::Tab,
                    physical_key: None,
                    pressed,
                    repeat: false,
                    modifiers: Modifiers::NONE,
                }],
                &mut time,
            );
        }
    }
    let (action, _) = frame(
        &ctx,
        &mut app,
        vec![Event::Key {
            key: Key::Enter,
            physical_key: None,
            pressed: true,
            repeat: false,
            modifiers: Modifiers::NONE,
        }],
        &mut time,
    );
    assert_eq!(
        action,
        Some(Action::SelectMonitor(1)),
        "Focused screen accepts keyboard Enter"
    );
    let (_, output) = click(&ctx, &mut app, &output, "Copy code", &mut time);
    assert!(output
        .platform_output
        .commands
        .contains(&egui::OutputCommand::CopyText("123456".into())));
    let (_, output) = click(
        &ctx,
        &mut app,
        &output,
        "Diagnostics & paired tablets",
        &mut time,
    );
    let (action, _) = click(&ctx, &mut app, &output, "Forget paired tablets", &mut time);
    assert_eq!(action, None, "Opening confirmation cannot forget a tablet");
    let (_, output) = frame(&ctx, &mut app, vec![], &mut time);
    let (action, _) = click(&ctx, &mut app, &output, "Cancel", &mut time);
    assert_eq!(action, None);
    assert_eq!(app.state.remembered, 2);
    let (_, output) = frame(&ctx, &mut app, vec![], &mut time);
    click(&ctx, &mut app, &output, "Forget paired tablets", &mut time);
    let (_, output) = frame(&ctx, &mut app, vec![], &mut time);
    let (action, _) = click(&ctx, &mut app, &output, "Forget all tablets", &mut time);
    assert_eq!(action, Some(Action::ForgetTablets));
    assert_eq!(app.state.remembered, 0);
    let (_, output) = frame(&ctx, &mut app, vec![], &mut time);
    click(&ctx, &mut app, &output, "QR code (iPad)", &mut time);
    let (_, output) = frame(&ctx, &mut app, vec![], &mut time);
    text_position(&output, "Connect your iPad");
    println!("Actual renderer interactions passed: monitor click, keyboard Enter, pairing clipboard, forget cancel/confirm, QR open.");
}

fn main() -> eframe::Result {
    let args: Vec<_> = std::env::args().collect();
    if args.iter().any(|arg| arg == "--verify") {
        verify();
        return Ok(());
    }
    let value = |name: &str| {
        args.iter()
            .position(|arg| arg == name)
            .and_then(|index| args.get(index + 1))
    };
    let width: f32 = value("--width").map_or(640.0, |value| value.parse().unwrap());
    let height: f32 = value("--height").map_or(760.0, |value| value.parse().unwrap());
    let show_qr = value("--state").is_some_and(|value| value == "qr");
    let mut app = Preview::new(
        value("--state").map_or("idle", String::as_str),
        value("--screenshot").map(PathBuf::from),
    );
    eframe::run_native(
        "DeskInk UI preview",
        eframe::NativeOptions {
            viewport: egui::ViewportBuilder::default()
                .with_inner_size([width, height])
                .with_min_inner_size([480.0, 560.0]),
            ..Default::default()
        },
        Box::new(move |cc| {
            design::install(&cc.egui_ctx);
            if show_qr {
                // Prepare panel state without carrying an unrelated synthetic
                // frame clock into the native viewport's animations.
                let prepare = egui::Context::default();
                design::install(&prepare);
                let mut time = 0.0;
                frame(&prepare, &mut app, vec![], &mut time);
                let (_, output) = frame(&prepare, &mut app, vec![], &mut time);
                click(&prepare, &mut app, &output, "QR code (iPad)", &mut time);
            }
            Ok(Box::new(app))
        }),
    )
}
