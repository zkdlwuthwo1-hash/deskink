use crate::{
    capture::OutputInfo,
    transport::link::{LinkId, PeerId},
};
use deskink_core::InkSession;
use parking_lot::Mutex;
use std::sync::atomic::{AtomicU64, Ordering};

pub const HOST_CAP: u16 = 0x1000;
pub const INPUT_MAGIC: &[u8; 4] = b"DI01";

pub struct AnnotationState {
    pub output: OutputInfo,
    pub overlay_handle: usize,
    pub ink: Mutex<InkSession>,
    pub revision: AtomicU64,
    binding: Mutex<(u32, u32)>,
    pub repaint: Mutex<Option<eframe::egui::Context>>,
}

impl AnnotationState {
    pub fn new(output: OutputInfo, overlay_handle: usize) -> Self {
        Self {
            ink: Mutex::new(InkSession::new(0, 0, output.width, output.height)),
            output,
            overlay_handle,
            revision: AtomicU64::new(0),
            binding: Mutex::new((0, 0)),
            repaint: Mutex::new(None),
        }
    }

    /// Called only after the existing pairing/peer authentication has succeeded.
    /// First slice intentionally accepts reliable USB events only; UDP needs an
    /// acknowledged/retransmitted input channel before it can safely draw.
    pub fn receive(
        &self,
        peer: PeerId,
        active_peer: Option<PeerId>,
        session: Option<u32>,
        epoch: u32,
        bytes: &[u8],
    ) -> bool {
        if active_peer != Some(peer) || !matches!(peer.link, LinkId::Usb { .. }) {
            return false;
        }
        let Some(session) = session.filter(|id| *id != 0) else {
            return false;
        };
        let mut binding = self.binding.lock();
        let mut ink = self.ink.lock();
        if *binding != (session, epoch) {
            ink.reconnect(session, epoch);
            *binding = (session, epoch);
        }
        let previous = ink.accepted_sequence();
        if ink.receive(bytes).is_err() {
            return false;
        }
        if ink.accepted_sequence() != previous {
            self.revision.fetch_add(1, Ordering::Release);
            if let Some(context) = &*self.repaint.lock() {
                context.request_repaint();
            }
        }
        true
    }
}
