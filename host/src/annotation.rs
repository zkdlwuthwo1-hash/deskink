use crate::{capture::OutputInfo, transport::link::PeerId};
use deskink_core::InkSession;
use parking_lot::Mutex;
use std::collections::BTreeMap;
use std::sync::{
    atomic::{AtomicU64, Ordering},
    Arc,
};
use std::time::{Duration, Instant};

pub const HOST_CAP: u16 = 0x1000;
pub const TOOLS_CAP: u16 = 0x2000;
pub const MONITOR_CAP: u16 = 0x4000;
pub const INPUT_MAGIC: &[u8; 4] = b"DI01";
pub const LAN_INPUT_MAGIC: &[u8; 4] = b"DI02";
pub const RELIABLE_INK_CAP: u16 = 0x8000;

#[derive(serde::Deserialize)]
struct InputBinding {
    version: u32,
    session: u32,
    epoch: u32,
    seq: u64,
}

#[derive(Default)]
struct LanInk {
    binding: Option<(PeerId, u32, u32)>,
    accepted: u64,
    pending: BTreeMap<u64, (Instant, Vec<u8>)>,
}

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
    lan_ink: Mutex<LanInk>,
    renderer: Mutex<Option<std::sync::mpsc::SyncSender<()>>>,
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
            lan_ink: Mutex::new(LanInk::default()),
            renderer: Mutex::new(None),
            repaint: Mutex::new(None),
        }
    }

    pub(crate) fn attach_renderer(&self, wake: std::sync::mpsc::SyncSender<()>) {
        *self.renderer.lock() = Some(wake);
    }

    fn request_presentation(&self) {
        if let Some(wake) = &*self.renderer.lock() {
            let _ = wake.try_send(());
        } else if let Some(context) = &*self.repaint.lock() {
            context.request_repaint();
        }
    }

    /// Called only after the existing pairing/peer authentication has succeeded.
    /// Raw DI01 remains USB-only. LAN callers use receive_lan for ordered delivery.
    pub fn receive(
        &self,
        peer: PeerId,
        active_peer: Option<PeerId>,
        session: Option<u32>,
        epoch: u32,
        bytes: &[u8],
    ) -> bool {
        if active_peer != Some(peer) || !peer.link.is_usb() {
            return false;
        }
        self.receive_authenticated(session, epoch, bytes)
    }

    fn receive_authenticated(&self, session: Option<u32>, epoch: u32, bytes: &[u8]) -> bool {
        let Some(session) = session.filter(|id| *id != 0) else {
            return false;
        };
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
            self.request_presentation();
        }
        true
    }

    /// LAN-only reliable input. In-order samples are applied immediately, without
    /// waiting for a timer or a batch. Only reordered samples occupy the window.
    /// The cumulative ACK lets clients recover both lost input and lost ACKs.
    pub fn receive_lan(
        &self,
        peer: PeerId,
        active_peer: Option<PeerId>,
        session: Option<u32>,
        epoch: u32,
        bytes: &[u8],
        now: Instant,
    ) -> Option<[u8; 20]> {
        if peer.link != crate::transport::link::LinkId::Udp
            || active_peer != Some(peer)
            || bytes.len() > 1024
            || epoch == 0
        {
            return None;
        }
        let session = session.filter(|id| *id != 0)?;
        let message: InputBinding = serde_json::from_slice(bytes).ok()?;
        if message.version != 1
            || message.session != session
            || message.epoch != epoch
            || message.seq == 0
        {
            return None;
        }
        let current_epoch = self.binding.lock().1;
        if current_epoch != 0 && (epoch.wrapping_sub(current_epoch) as i32) < 0 {
            return None;
        }
        let mut lan = self.lan_ink.lock();
        let key = (peer, session, epoch);
        if lan.binding != Some(key) {
            *lan = LanInk {
                binding: Some(key),
                ..LanInk::default()
            };
        }
        if message.seq > lan.accepted {
            if message.seq - lan.accepted > 256
                || lan
                    .pending
                    .values()
                    .any(|(at, _)| now.saturating_duration_since(*at) >= Duration::from_millis(250))
            {
                return None;
            }
            lan.pending
                .entry(message.seq)
                .or_insert_with(|| (now, bytes.to_vec()));
            while let Some(next) = lan.accepted.checked_add(1) {
                let Some((_, body)) = lan.pending.remove(&next) else {
                    break;
                };
                if !self.receive_authenticated(Some(session), epoch, &body) {
                    break;
                }
                lan.accepted = next;
            }
        }
        let mut ack = [0; 20];
        ack[..4].copy_from_slice(b"DA02");
        ack[4..8].copy_from_slice(&session.to_le_bytes());
        ack[8..12].copy_from_slice(&epoch.to_le_bytes());
        ack[12..].copy_from_slice(&lan.accepted.to_le_bytes());
        Some(ack)
    }

    pub fn finish_stream(&self, epoch: u32) {
        let mut binding = self.binding.lock();
        if binding.1 != epoch {
            return;
        }
        self.ink.lock().reconnect(0, 0);
        *binding = (0, 0);
        self.revision.fetch_add(1, Ordering::Release);
        self.request_presentation();
    }
}
