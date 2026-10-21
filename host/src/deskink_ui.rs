//! DeskInk's native presentation layer. Snapshots contain observed state only;
//! rendering never holds a transport, pairing, annotation, or statistics lock.
use eframe::egui::{
    self, pos2, vec2, Align, FontId, Frame, Layout, Margin, Rect, Sense, Stroke, StrokeKind, Ui,
    WidgetInfo, WidgetType,
};

pub use crate::gui::icons::Icon;
pub use crate::gui::theme::{
    faint, heading, medium, mono, mono_medium, muted, overline, semibold, strong, tint,
    tint_border, title, ACCENT, BORDER, BORDER_STRONG, CANVAS, DANGER, ON_ACCENT, SURFACE,
    SURFACE_HOVER, SURFACE_RAISED, TEXT, TEXT_FAINT, TEXT_MUTED, WARNING,
};
pub use crate::gui::widgets::{
    badge, button, button_ex, card, divider, paragraph, section_label, Tone,
};

pub fn install(ctx: &egui::Context) {
    crate::gui::theme::install(ctx);
    // Device names may contain Hangul. Use the installed Windows face only as
    // a fallback; no font is redistributed and no I/O runs while painting.
    #[cfg(windows)]
    if let Some(path) = std::env::var_os("WINDIR")
        .map(std::path::PathBuf::from)
        .map(|root| root.join("Fonts").join("malgun.ttf"))
    {
        if let Ok(bytes) = std::fs::read(path) {
            use egui::epaint::text::{FontInsert, FontPriority, InsertFontFamily};
            ctx.add_font(FontInsert::new(
                "windows-hangul-fallback",
                egui::FontData::from_owned(bytes),
                [
                    egui::FontFamily::Proportional,
                    egui::FontFamily::Monospace,
                    medium(),
                    semibold(),
                    mono_medium(),
                ]
                .into_iter()
                .map(|family| InsertFontFamily {
                    family,
                    priority: FontPriority::Lowest,
                })
                .collect(),
            ));
        }
    }
}

#[derive(Clone)]
pub struct Monitor {
    pub name: String,
    pub width: u32,
    pub height: u32,
    pub primary: bool,
    pub active: bool,
}

#[derive(Clone)]
pub struct Connection {
    pub device_name: String,
    pub usb: bool,
}

pub struct DashboardSnapshot {
    pub monitors: Vec<Monitor>,
    pub connection: Option<Connection>,
    pub lan_enabled: bool,
    pub pairing_code: u32,
    pub remembered: usize,
    pub capture_fps: f64,
    pub encode_fps: f64,
    pub codec: String,
    pub error: Option<String>,
}

#[derive(Debug, PartialEq, Eq)]
pub enum Action {
    SelectMonitor(usize),
    ForgetTablets,
}

/// The real dashboard, also usable by a native preview without opening a
/// listener or creating an annotation window. The caller executes actions.
pub fn dashboard(
    ui: &mut Ui,
    state: &DashboardSnapshot,
    network: impl FnOnce(&mut Ui),
    updates: impl FnOnce(&mut Ui),
) -> Option<Action> {
    let mut action = None;
    ui.painter().rect_filled(ui.max_rect(), 0.0, CANVAS);
    egui::ScrollArea::vertical().auto_shrink([false, false]).show(ui, |ui| {
        Frame::new().inner_margin(Margin::same(24)).show(ui, |ui| {
            ui.set_width(ui.available_width());
            ui.spacing_mut().item_spacing.y = 6.0;
            header(ui, state);
            if let Some(error) = &state.error {
                ui.add_space(8.0);
                Frame::new().fill(tint(DANGER)).stroke(Stroke::new(1.0, tint_border(DANGER)))
                    .corner_radius(10.0).inner_margin(14).show(ui, |ui| {
                        ui.set_width(ui.available_width());
                        ui.label(strong("DeskInk needs your attention").color(DANGER));
                        paragraph(ui, muted(error).color(TEXT));
                    });
            }
            ui.add_space(12.0);
            ui.horizontal(|ui| {
                ui.label(heading("Choose your screen"));
                ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                    ui.label(faint("Ink stays with each screen"));
                });
            });
            let columns = if ui.available_width() >= 390.0 { 2 } else { 1 };
            let gap = 12.0;
            let width = (ui.available_width() - gap * (columns - 1) as f32) / columns as f32;
            egui::Grid::new("deskink-monitors").num_columns(columns).spacing(vec2(gap, gap)).show(ui, |ui| {
                for (index, monitor) in state.monitors.iter().enumerate() {
                    if monitor_tile(ui, index, monitor, width).clicked() {
                        action = Some(Action::SelectMonitor(index));
                    }
                    if (index + 1) % columns == 0 { ui.end_row(); }
                }
            });
            if state.monitors.is_empty() {
                paragraph(ui, muted("No screen is available. Connect a display and reopen DeskInk."));
            }
            ui.add_space(12.0);
            pairing(ui, state.pairing_code, state.remembered);
            ui.add_space(10.0);
            network(ui);
            ui.add_space(10.0);
            updates(ui);
            ui.add_space(4.0);
            egui::CollapsingHeader::new(strong("Diagnostics & paired tablets"))
                .id_salt("deskink-diagnostics").show(ui, |ui| {
                    ui.add_space(8.0);
                    ui.horizontal_wrapped(|ui| {
                        crate::gui::widgets::metric(ui, "Capture", &format!("{:.0}", state.capture_fps), "fps", 21.0);
                        ui.add_space(24.0);
                        crate::gui::widgets::metric(ui, "Encode", &format!("{:.0}", state.encode_fps), "fps", 21.0);
                        ui.add_space(24.0);
                        ui.vertical(|ui| {
                            ui.label(muted("Stream"));
                            ui.label(mono(if state.codec.is_empty() { "Not started" } else { &state.codec }, 15.0));
                        });
                    });
                    ui.add_space(8.0);
                    divider(ui);
                    ui.add_space(6.0);
                    if state.remembered > 0 {
                        ui.label(muted(format!("{} saved tablet{} on this PC", state.remembered, if state.remembered == 1 { "" } else { "s" })));
                        if button(ui, Tone::Ghost, Some(Icon::Lock), "Forget paired tablets").clicked() {
                            ui.ctx().data_mut(|data| data.insert_temp(egui::Id::new("deskink-forget-confirm"), true));
                        }
                    } else {
                        ui.label(muted("No paired tablets yet. Enter the code above on your tablet."));
                    }
                });
            ui.add_space(14.0);
            divider(ui);
            ui.add_space(8.0);
            paragraph(ui, faint("Choose pens and colors on your tablet. Closing DeskInk clears ink on all screens."));
        });
    });
    if forget_confirmation(ui.ctx(), state.remembered) {
        action = Some(Action::ForgetTablets);
    }
    action
}

fn header(ui: &mut Ui, state: &DashboardSnapshot) {
    ui.horizontal(|ui| {
        let (mark, _) = ui.allocate_exact_size(vec2(40.0, 40.0), Sense::hover());
        ui.painter().rect_filled(mark, 11.0, ACCENT);
        let p = |x, y| mark.min + vec2(x, y);
        ui.painter().add(egui::Shape::closed_line(
            vec![
                p(11.0, 28.0),
                p(13.5, 19.5),
                p(25.0, 8.0),
                p(32.0, 15.0),
                p(20.5, 26.5),
            ],
            Stroke::new(2.0, ON_ACCENT),
        ));
        ui.painter()
            .line_segment([p(13.5, 19.5), p(20.5, 26.5)], Stroke::new(2.0, ON_ACCENT));
        ui.add_space(4.0);
        ui.vertical(|ui| {
            ui.spacing_mut().item_spacing.y = 1.0;
            ui.label(title("DeskInk"));
            ui.label(muted("Your Windows writing companion"));
        });
    });
    ui.add_space(10.0);
    ui.horizontal_wrapped(|ui| {
        if let Some(connection) = &state.connection {
            badge(
                ui,
                if connection.usb {
                    "USB connected"
                } else {
                    "Wi-Fi / LAN connected"
                },
                ACCENT,
            );
            ui.label(strong("Tablet connected"));
            ui.add(egui::Label::new(muted(&connection.device_name)).truncate())
                .on_hover_text(&connection.device_name);
        } else {
            badge(ui, "Ready to connect", TEXT_MUTED);
            ui.add(
                egui::Label::new(muted(if state.lan_enabled {
                    "Open DeskInk on your tablet. USB or Wi-Fi is ready."
                } else {
                    "Connect by USB and open DeskInk on your tablet."
                }))
                .wrap(),
            );
        }
    });
}

fn monitor_tile(ui: &mut Ui, index: usize, monitor: &Monitor, width: f32) -> egui::Response {
    let (rect, response) = ui.allocate_exact_size(vec2(width, 86.0), Sense::click());
    let label = format!(
        "Screen {} · {} by {}{}",
        index + 1,
        monitor.width,
        monitor.height,
        if monitor.active { " · Selected" } else { "" }
    );
    response.widget_info(|| {
        WidgetInfo::selected(WidgetType::SelectableLabel, true, monitor.active, &label)
    });
    let fill = if monitor.active {
        tint(ACCENT)
    } else if response.hovered() {
        SURFACE_HOVER
    } else {
        SURFACE
    };
    let border = if response.has_focus() {
        ACCENT
    } else if monitor.active {
        tint_border(ACCENT)
    } else {
        BORDER
    };
    ui.painter().rect(
        rect,
        12.0,
        fill,
        Stroke::new(if response.has_focus() { 2.0 } else { 1.0 }, border),
        StrokeKind::Inside,
    );
    let icon_rect = Rect::from_min_size(rect.min + vec2(16.0, 16.0), vec2(22.0, 22.0));
    crate::gui::icons::paint(
        ui.painter(),
        icon_rect,
        Icon::Display,
        if monitor.active { ACCENT } else { TEXT_MUTED },
    );
    ui.painter().text(
        rect.min + vec2(49.0, 19.0),
        egui::Align2::LEFT_TOP,
        format!("Screen {}", index + 1),
        FontId::new(15.0, semibold()),
        TEXT,
    );
    if monitor.active {
        crate::gui::icons::paint(
            ui.painter(),
            Rect::from_min_size(
                pos2(rect.right() - 30.0, rect.top() + 18.0),
                vec2(15.0, 15.0),
            ),
            Icon::Check,
            ACCENT,
        );
    }
    ui.painter().text(
        rect.min + vec2(16.0, 56.0),
        egui::Align2::LEFT_TOP,
        format!("{} × {}", monitor.width, monitor.height),
        FontId::new(13.0, mono_medium()),
        TEXT_MUTED,
    );
    if monitor.primary && width >= 225.0 {
        ui.painter().text(
            pos2(rect.right() - 16.0, rect.top() + 57.0),
            egui::Align2::RIGHT_TOP,
            "Main",
            FontId::new(11.5, medium()),
            TEXT_FAINT,
        );
    }
    response
        .on_hover_text(&monitor.name)
        .on_hover_cursor(egui::CursorIcon::PointingHand)
}

fn pairing(ui: &mut Ui, code: u32, remembered: usize) {
    card().inner_margin(Margin::same(16)).show(ui, |ui| {
        ui.set_width(ui.available_width());
        ui.spacing_mut().item_spacing.y = 4.0;
        ui.horizontal(|ui| {
            ui.label(heading("Pairing code"));
            ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                if remembered > 0 {
                    ui.label(faint(format!("{remembered} paired")));
                }
            });
        });
        ui.horizontal(|ui| {
            let formatted = format!("{code:06}");
            ui.label(mono(&formatted, 40.0).extra_letter_spacing(3.5));
            ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                let now = ui.input(|input| input.time);
                let copied = ui.ctx().data(|data| {
                    data.get_temp::<f64>(egui::Id::new("deskink-code-copied"))
                        .is_some_and(|until| until > now)
                });
                if button_ex(
                    ui,
                    Tone::Primary,
                    Some(if copied { Icon::Check } else { Icon::Copy }),
                    if copied { "Copied" } else { "Copy code" },
                    true,
                    120.0,
                )
                .clicked()
                {
                    ui.ctx().copy_text(formatted);
                    ui.ctx().data_mut(|data| {
                        data.insert_temp(egui::Id::new("deskink-code-copied"), now + 2.0)
                    });
                    ui.ctx()
                        .request_repaint_after(std::time::Duration::from_secs(2));
                }
            });
        });
        ui.add_space(2.0);
        paragraph(
            ui,
            muted("Enter this code once on your tablet. Paired tablets reconnect automatically."),
        );
    });
}

fn forget_confirmation(ctx: &egui::Context, remembered: usize) -> bool {
    let id = egui::Id::new("deskink-forget-confirm");
    let mut open = remembered > 0 && ctx.data(|data| data.get_temp::<bool>(id).unwrap_or(false));
    let mut confirmed = false;
    let mut cancelled = false;
    if open {
        egui::Window::new("Forget paired tablets?").id(id).open(&mut open)
            .collapsible(false).resizable(false).default_width(360.0).show(ctx, |ui| {
                paragraph(ui, muted("This removes every saved tablet from this PC. They will need a pairing code to connect again."));
                ui.add_space(10.0);
                ui.horizontal_wrapped(|ui| {
                    cancelled = button(ui, Tone::Secondary, None, "Cancel").clicked();
                    confirmed = button(ui, Tone::Danger, Some(Icon::Lock), "Forget all tablets").clicked();
                });
            });
    }
    if ctx.input(|input| input.key_pressed(egui::Key::Escape)) {
        cancelled = true;
    }
    ctx.data_mut(|data| data.insert_temp(id, open && !confirmed && !cancelled));
    confirmed
}
