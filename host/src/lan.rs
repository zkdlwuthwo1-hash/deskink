//! Opt-in LAN setup for the DeskInk controller. No work runs on the ink/video path.
use crate::control::{SharedControl, SupervisorCommand};
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
        ui.collapsing("Wi-Fi / LAN", |ui| {
            let connected = shared.client_connected();
            let mut enabled = self.enabled;
            if ui
                .add_enabled(
                    !connected,
                    egui::Checkbox::new(&mut enabled, "Allow Wi-Fi / LAN"),
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
                ui.small("Disconnect the tablet before changing network access.");
            }
            if self.enabled {
                ui.small("Use DeskInk 0.5.0+ on your tablet, on the same trusted router.");
                ui.small("PC Ethernet + tablet Wi-Fi works. Traffic is not encrypted.");
                if let Some(address) = self.address {
                    ui.horizontal(|ui| {
                        ui.label(format!(
                            "PC address: {}    Port: {}",
                            address.ip(),
                            address.port()
                        ));
                        if ui.button("Copy IP").clicked() {
                            ui.ctx().copy_text(address.ip().to_string());
                        }
                    });
                    ui.horizontal(|ui| {
                        if ui.button("QR code (iPad)").clicked() {
                            self.refresh();
                            self.show_qr = true;
                        }
                        if ui.button("Refresh IP").clicked() {
                            self.refresh();
                        }
                    });
                } else {
                    ui.label("No LAN address found. Connect Wi-Fi or Ethernet.");
                    if ui.button("Refresh IP").clicked() {
                        self.refresh();
                    }
                }
                ui.small("iPad: Scan QR code or enter IP. Android: Wi-Fi / LAN, then IP.");
                ui.small(
                    "First connection asks for the pairing code above. Unplug USB to use Wi-Fi.",
                );
                ui.hyperlink_to(
                    "Connection and Windows firewall help",
                    "https://zkdlwuthwo1-hash.github.io/deskink/README.html#wireless",
                );
            } else {
                ui.small("USB works as before. Network access is off by default.");
            }
            if let Some(error) = &self.error {
                ui.colored_label(egui::Color32::RED, error);
            }
        });
        if self.show_qr {
            egui::Window::new("Scan with DeskInk on iPad").open(&mut self.show_qr).collapsible(false).resizable(false).show(ui.ctx(), |ui| {
                if let Some(code) = &self.qr {
                    let cells = code.width();
                    let size = 280.0;
                    let (rect, _) = ui.allocate_exact_size(egui::vec2(size, size), egui::Sense::hover());
                    ui.painter().rect_filled(rect, 0.0, egui::Color32::WHITE);
                    let module = (size / (cells + 8) as f32).floor();
                    let origin = rect.min + egui::Vec2::splat((size - cells as f32 * module) / 2.0);
                    for y in 0..cells { for x in 0..cells {
                        if code[(x, y)] == qrcode::Color::Dark {
                            ui.painter().rect_filled(egui::Rect::from_min_size(origin + egui::vec2(x as f32 * module, y as f32 * module), egui::Vec2::splat(module)), 0.0, egui::Color32::BLACK);
                        }
                    }}
                    if let Some(address) = self.address { ui.label(address.to_string()); }
                    ui.label("This QR contains the address only. Enter the PC pairing code when asked.");
                }
            });
        }
    }
}
