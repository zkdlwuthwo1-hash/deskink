//! Opt-in LAN setup for the DeskInk controller. No work runs on the ink/video path.
use crate::control::{SharedControl, SupervisorCommand};
use crate::deskink_ui as design;
use eframe::egui;
use std::{
    net::SocketAddr,
    path::PathBuf,
    sync::{atomic::Ordering, mpsc::Sender},
};

pub const PORT: u16 = 9876;

pub struct LanPanel {
    path: PathBuf,
    enabled: bool,
    address: Option<SocketAddr>,
    qr: Option<qrcode::QrCode>,
    show_qr: bool,
    error: Option<String>,
}

impl LanPanel {
    pub fn new(path: PathBuf) -> Self {
        let enabled = std::fs::read_to_string(&path).is_ok_and(|text| text.trim() == "enabled");
        let mut panel = Self {
            path,
            enabled,
            address: None,
            qr: None,
            show_qr: false,
            error: None,
        };
        if enabled {
            panel.refresh();
        }
        panel
    }

    pub fn enabled(&self) -> bool {
        self.enabled
    }

    fn refresh(&mut self) {
        self.address = crate::gui::detect_local_ip(PORT).parse().ok();
        self.qr = self.address.and_then(|address| {
            qrcode::QrCode::new(format!("eternaldisplay://{address}").as_bytes()).ok()
        });
    }

    pub fn show(
        &mut self,
        ui: &mut egui::Ui,
        shared: &SharedControl,
        commands: &Sender<SupervisorCommand>,
    ) {
        design::card().show(ui, |ui| {
            ui.set_width(ui.available_width());
            ui.horizontal(|ui| {
                ui.label(design::heading("Wi-Fi / LAN"));
                ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                    design::badge(
                        ui,
                        if self.enabled { "Enabled" } else { "Off" },
                        if self.enabled {
                            design::ACCENT
                        } else {
                            design::TEXT_MUTED
                        },
                    );
                });
            });
            if !self.enabled {
                design::paragraph(
                    ui,
                    design::muted("An optional connection through your local network."),
                );
            }
            ui.add_space(4.0);
            let connected = shared.client_connected();
            let mut enabled = self.enabled;
            if ui
                .add_enabled(
                    !connected,
                    egui::Checkbox::new(&mut enabled, design::strong("Allow Wi-Fi / LAN")),
                )
                .changed()
            {
                let saved = (|| -> std::io::Result<()> {
                    if let Some(parent) = self.path.parent() {
                        std::fs::create_dir_all(parent)?;
                    }
                    std::fs::write(&self.path, if enabled { "enabled\n" } else { "disabled\n" })
                })();
                match saved {
                    Ok(()) => {
                        self.enabled = enabled;
                        shared.lan_enabled.store(enabled, Ordering::SeqCst);
                        if commands.send(SupervisorCommand::Restart).is_err() {
                            self.error = Some(
                                "Close and reopen DeskInk to apply the network setting.".into(),
                            );
                        }
                        if enabled {
                            self.refresh();
                        }
                    }
                    Err(error) => {
                        self.error = Some(format!("Could not save network setting: {error}"))
                    }
                }
            }
            if connected {
                design::paragraph(
                    ui,
                    design::muted("Disconnect the tablet before changing network access."),
                );
            }
            if self.enabled {
                ui.add_space(4.0);
                design::divider(ui);
                design::section_label(ui, "PC address");
                if let Some(address) = self.address {
                    ui.horizontal_wrapped(|ui| {
                        ui.label(design::mono(address.ip().to_string(), 22.0));
                        if design::button(
                            ui,
                            design::Tone::Secondary,
                            Some(design::Icon::Copy),
                            "Copy IP",
                        )
                        .clicked()
                        {
                            ui.ctx().copy_text(address.ip().to_string());
                        }
                    });
                    ui.label(
                        design::mono(format!("Port {}", address.port()), 12.5)
                            .color(design::TEXT_MUTED),
                    );
                    ui.add_space(2.0);
                    ui.horizontal_wrapped(|ui| {
                        if design::button(
                            ui,
                            design::Tone::Secondary,
                            Some(design::Icon::Qr),
                            "QR code (iPad)",
                        )
                        .clicked()
                        {
                            self.refresh();
                            self.show_qr = true;
                        }
                        if design::button(
                            ui,
                            design::Tone::Ghost,
                            Some(design::Icon::Refresh),
                            "Refresh IP",
                        )
                        .clicked()
                        {
                            self.refresh();
                        }
                    });
                } else {
                    design::paragraph(ui, design::strong("No LAN address found"));
                    design::paragraph(
                        ui,
                        design::muted("Connect this PC to Wi-Fi or Ethernet, then refresh."),
                    );
                    if design::button(
                        ui,
                        design::Tone::Secondary,
                        Some(design::Icon::Refresh),
                        "Refresh IP",
                    )
                    .clicked()
                    {
                        self.refresh();
                    }
                }
                ui.add_space(4.0);
                design::paragraph(
                    ui,
                    design::muted(
                        "First connection: enter the pairing code above. Unplug USB to use Wi-Fi.",
                    ),
                );
                egui::CollapsingHeader::new(design::muted("Connection help"))
                    .id_salt("deskink-lan-help")
                    .default_open(false)
                    .show(ui, |ui| {
                        design::paragraph(
                            ui,
                            design::muted(
                                "iPad: scan the QR code or enter the IP. Android: choose Wi-Fi / LAN, then enter the IP.",
                            ),
                        );
                        design::paragraph(
                            ui,
                            design::muted(
                                "Requires DeskInk 0.5.0+ on your tablet and the same trusted router. PC Ethernet + tablet Wi-Fi works. Traffic is not encrypted.",
                            ),
                        );
                        ui.hyperlink_to(
                            design::muted("Connection and Windows firewall help")
                                .color(design::ACCENT),
                            "https://zkdlwuthwo1-hash.github.io/deskink/README.html#wireless",
                        );
                    });
            } else {
                design::paragraph(
                    ui,
                    design::muted("USB is ready to use. Network access is off by default."),
                );
            }
            if let Some(error) = &self.error {
                ui.add_space(4.0);
                design::paragraph(ui, design::muted(error).color(design::DANGER));
            }
        });
        if self.show_qr {
            egui::Window::new("Connect your iPad")
                .open(&mut self.show_qr)
                .collapsible(false)
                .resizable(false)
                .default_width(320.0)
                .show(ui.ctx(), |ui| {
                    ui.set_width(320.0);
                    if let Some(code) = &self.qr {
                        design::paragraph(
                            ui,
                            design::muted("Open DeskInk on your iPad and scan this code."),
                        );
                        ui.add_space(8.0);
                        ui.horizontal(|ui| {
                            ui.spacing_mut().item_spacing.x = 0.0;
                            ui.add_space(20.0);
                            let cells = code.width();
                            let size = 280.0;
                            let (rect, _) = ui.allocate_exact_size(
                                egui::vec2(size, size),
                                egui::Sense::hover(),
                            );
                            ui.painter().rect_filled(rect, 0.0, egui::Color32::WHITE);
                            let module = (size / (cells + 8) as f32).floor();
                            let origin =
                                rect.min + egui::Vec2::splat((size - cells as f32 * module) / 2.0);
                            for y in 0..cells {
                                for x in 0..cells {
                                    if code[(x, y)] == qrcode::Color::Dark {
                                        ui.painter().rect_filled(
                                            egui::Rect::from_min_size(
                                                origin
                                                    + egui::vec2(
                                                        x as f32 * module,
                                                        y as f32 * module,
                                                    ),
                                                egui::Vec2::splat(module),
                                            ),
                                            0.0,
                                            egui::Color32::BLACK,
                                        );
                                    }
                                }
                            }
                        });
                        ui.add_space(8.0);
                        if let Some(address) = self.address {
                            ui.label(design::mono(address.to_string(), 13.5));
                        }
                        design::paragraph(
                            ui,
                            design::muted(
                                "This QR contains the address only. Enter the PC pairing code when asked.",
                            ),
                        );
                    }
                });
        }
    }
}
