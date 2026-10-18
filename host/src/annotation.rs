use crate::{
    capture::OutputInfo,
    transport::link::{LinkId, PeerId},
};
use deskink_core::InkSession;
use parking_lot::Mutex;
use std::sync::{
    atomic::{AtomicU64, Ordering},
    Arc,
};

pub const HOST_CAP: u16 = 0x1000;
pub const TOOLS_CAP: u16 = 0x2000;
pub const MONITOR_CAP: u16 = 0x4000;
pub const INPUT_MAGIC: &[u8; 4] = b"DI01";

/// The GUI selects the next monitor. Each pipeline holds its own snapshot so
/// retiring capture/transport threads can never route old input to a new board.
pub struct AnnotationWorkspace {
    selected: Mutex<Arc<AnnotationState>>,
}

impl AnnotationWorkspace {
    pub fn new(selected: Arc<AnnotationState>) -> Self {
        Self {
            selected: Mutex::new(selected),
        }
    }
    pub fn active(&self) -> Arc<AnnotationState> {
        self.selected.lock().clone()
    }
    pub fn select(&self, selected: Arc<AnnotationState>) {
        *self.selected.lock() = selected;
    }
    pub fn snapshot(&self) -> Self {
        Self::new(self.active())
    }
}

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
        #[derive(serde::Deserialize)]
        struct InputBinding {
            version: u32,
            session: u32,
            epoch: u32,
        }
        if bytes.len() > 1024 {
            return false;
        }
        let Ok(message) = serde_json::from_slice::<InputBinding>(bytes) else {
            return false;
        };
        // Reject stale envelopes BEFORE reconnect mutates sequence/history.
        if message.version != 1
            || message.session != session
            || message.epoch != epoch
            || epoch == 0
        {
            return false;
        }
        let mut binding = self.binding.lock();
        if binding.1 != 0 && (epoch.wrapping_sub(binding.1) as i32) < 0 {
            return false;
        }
        let mut ink = self.ink.lock();
        ink.advance_time(crate::clock::host_now_us() / 1000);
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

    pub fn finish_stream(&self, epoch: u32) {
        let mut binding = self.binding.lock();
        if binding.1 != epoch {
            return;
        }
        self.ink.lock().reconnect(0, 0);
        *binding = (0, 0);
        self.revision.fetch_add(1, Ordering::Release);
        if let Some(context) = &*self.repaint.lock() {
            context.request_repaint();
        }
    }
}
