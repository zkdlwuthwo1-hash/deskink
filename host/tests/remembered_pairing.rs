#![cfg(windows)]
use eternal_host::{control::SharedControl, transport::{link::PeerId, session::ConfigSource}};
use eternal_wire::v2::control::{ControlMessage, Hello2, HelloAck, HelloStatus, StreamConfig, CAP_DECODE_H264};
use std::{path::PathBuf, time::{Instant, SystemTime, UNIX_EPOCH}};

struct TestPc(PathBuf);
impl TestPc {
    fn new() -> Self { Self(std::env::temp_dir().join(format!("deskink-pairing-test-{}-{}.bin", std::process::id(), SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_nanos()))) }
}
impl Drop for TestPc { fn drop(&mut self) { let _ = std::fs::remove_file(&self.0); } }

fn start_deskink(pc: &TestPc) -> SharedControl {
    // The same startup path used by the DeskInk entry point, without capture/UI.
    let shared = SharedControl::new(9876, 15_000_000);
    *shared.pairing.lock() = eternal_host::pairing::Pairing::remembered(&pc.0).unwrap();
    shared
}

fn tablet() -> Hello2 {
    Hello2 {
        proto_min: 2, proto_max: 2, client_nonce: 1, listen_port: 9877,
        decoder_caps: CAP_DECODE_H264, feature_caps: 0,
        screen_px_w: 1920, screen_px_h: 1080, screen_pt_w: 960, screen_pt_h: 540,
        refresh_hz: 60, device_name: "Pairing test tablet".into(), device_id: 42,
        preferred_fps: 60, auth_token: [0; 16], pairing_code: 0,
    }
}

fn handshake(shared: &SharedControl, hello: Hello2) -> HelloAck {
    struct Config<'a>(&'a SharedControl);
    impl ConfigSource for Config<'_> {
        fn pairing(&self) -> Option<&parking_lot::Mutex<eternal_host::pairing::Pairing>> { Some(&self.0.pairing) }
        fn host_name(&self) -> String { "Pairing test PC".into() }
        fn stream_config(&self) -> StreamConfig {
            StreamConfig { stream_epoch: 1, width: 1280, height: 720, fps: 60, codec: 0, flags: 0, bitrate_bps: 15_000_000 }
        }
    }
    let actions = shared.session.lock().handle_control(PeerId::usb(7), 0,
        ControlMessage::Hello2(hello), &Config(shared), Instant::now());
    let (_, ControlMessage::HelloAck(ack)) = eternal_wire::v2::control::parse_control(&actions.replies[0].1).unwrap()
        else { panic!("Expected a handshake response") };
    ack
}

#[test]
fn a_registered_tablet_reconnects_after_the_windows_app_restarts_without_a_code() {
    let pc = TestPc::new();
    let first = start_deskink(&pc);
    let mut hello = tablet();
    hello.pairing_code = first.pairing.lock().code();
    let accepted = handshake(&first, hello.clone());
    assert_eq!(accepted.status, HelloStatus::Ok);
    hello.auth_token = accepted.auth_token;
    hello.pairing_code = 0;
    drop(first);
    // Do not deliver/save the replacement ACK on the tablet. Repeated restarts
    // must still accept the credential it actually possesses.
    for _ in 0..3 {
        let reopened = start_deskink(&pc);
        let ack = handshake(&reopened, hello.clone());
        assert_eq!(ack.status, HelloStatus::Ok,
            "A registered tablet must reconnect after Windows restarts without asking for another code");
        assert!(ack.auth_token != hello.auth_token,
            "New host runs still use a fresh ink-document identity");
    }
}

#[test]
fn remembering_one_pc_does_not_authorize_another_pc_or_another_tablet() {
    let first_pc = TestPc::new(); let second_pc = TestPc::new();
    let first = start_deskink(&first_pc);
    let mut hello = tablet(); hello.pairing_code = first.pairing.lock().code();
    let token = handshake(&first, hello.clone()).auth_token;
    drop(first);
    hello.pairing_code = 0; hello.auth_token = token;
    assert_eq!(handshake(&start_deskink(&second_pc), hello.clone()).status, HelloStatus::Unauthorized);
    let reopened = start_deskink(&first_pc);
    let mut stranger = hello.clone(); stranger.device_id = 99;
    assert_eq!(handshake(&reopened, stranger).status, HelloStatus::Unauthorized);
    assert_eq!(handshake(&reopened, hello).status, HelloStatus::Ok);
}

#[test]
fn forgetting_registration_revokes_current_and_persisted_credentials() {
    let pc = TestPc::new(); let host = start_deskink(&pc);
    let mut hello = tablet(); hello.pairing_code = host.pairing.lock().code();
    hello.auth_token = handshake(&host, hello.clone()).auth_token;
    hello.pairing_code = 0;
    host.pairing.lock().regenerate_token().unwrap();
    hello.client_nonce += 1;
    assert_eq!(handshake(&host, hello.clone()).status, HelloStatus::Unauthorized);
    drop(host);
    assert_eq!(handshake(&start_deskink(&pc), hello).status, HelloStatus::Unauthorized);
}

#[test]
fn corrupted_registration_never_silently_replaces_saved_trust_or_disables_pairing() {
    let pc = TestPc::new();
    std::fs::write(&pc.0, b"damaged registration").unwrap();
    assert!(eternal_host::pairing::Pairing::remembered(&pc.0).is_err());
    assert_eq!(std::fs::read(&pc.0).unwrap(), b"damaged registration");
}
