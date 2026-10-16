use eternal_host::{annotation::AnnotationState, capture::OutputInfo, transport::link::PeerId};

fn annotations() -> AnnotationState {
    AnnotationState::new(
        OutputInfo {
            adapter_index: 0,
            output_index: 0,
            device_name: "test-display".into(),
            width: 1920,
            height: 1080,
            left: 0,
            top: 0,
            is_primary: true,
            adapter_name: "test".into(),
        },
        0,
    )
}

const START: &[u8] = br#"{"version":1,"session":42,"epoch":3,"seq":1,"command":{"type":"begin","id":7,"brush":{"color":[255,80,40,255],"width":4},"point":{"x":100,"y":200,"pressure":0.4}}}"#;

#[test]
fn only_the_connected_usb_peer_can_change_the_visible_board() {
    let annotations = annotations();
    let active = PeerId::usb(7);
    assert!(!annotations.receive(PeerId::usb(8), Some(active), Some(42), 3, START));
    assert!(!annotations.receive(active, None, None, 3, START));
    let udp = PeerId::udp("127.0.0.1:1234".parse().unwrap());
    assert!(!annotations.receive(udp, Some(udp), Some(42), 3, START));
    assert!(annotations.ink.lock().board().strokes().is_empty());
    assert!(annotations.receive(active, Some(active), Some(42), 3, START));
    assert_eq!(annotations.ink.lock().board().strokes().len(), 1);
}
#[test]
fn deskink_requires_a_pairing_code_even_on_usb() {
    use eternal_host::pairing::Pairing;
    use eternal_wire::v2::control::{Hello2, HelloStatus, CAP_DECODE_H264};
    let mut pairing = Pairing::new(true, [17; 16]).unwrap();
    pairing.require_usb_code = true;
    let mut hello = Hello2 {
        proto_min: 2,
        proto_max: 2,
        client_nonce: 1,
        listen_port: 9876,
        decoder_caps: CAP_DECODE_H264,
        feature_caps: 0,
        screen_px_w: 1920,
        screen_px_h: 1080,
        screen_pt_w: 960,
        screen_pt_h: 540,
        refresh_hz: 60,
        device_name: "test iPad".into(),
        device_id: 1,
        preferred_fps: 60,
        auth_token: [0; 16],
        pairing_code: 0,
    };
    let peer = PeerId::usb(7);
    assert!(matches!(
        pairing.authorize(peer, &hello, std::time::Instant::now()),
        Err(HelloStatus::Unauthorized)
    ));
    hello.pairing_code = pairing.code();
    assert!(pairing
        .authorize(peer, &hello, std::time::Instant::now())
        .is_ok());
}

#[test]
fn repeated_usb_pairing_guesses_are_limited_across_reconnections() {
    use eternal_host::pairing::Pairing;
    use eternal_wire::v2::control::{Hello2, HelloStatus};
    use std::time::{Duration, Instant};
    let mut pairing = Pairing::new(true, [17; 16]).unwrap();
    pairing.require_usb_code = true;
    let mut hello = Hello2 {
        proto_min: 2,
        proto_max: 2,
        client_nonce: 1,
        listen_port: 9876,
        decoder_caps: 1,
        feature_caps: 0,
        screen_px_w: 1920,
        screen_px_h: 1080,
        screen_pt_w: 960,
        screen_pt_h: 540,
        refresh_hz: 60,
        device_name: "test iPad".into(),
        device_id: 1,
        preferred_fps: 60,
        auth_token: [0; 16],
        pairing_code: 0,
    };
    let now = Instant::now();
    for attempt in 1..=5 {
        hello.client_nonce = attempt;
        let result = pairing.authorize(PeerId::usb(attempt), &hello, now);
        let expected = if attempt == 5 {
            HelloStatus::RateLimited
        } else {
            HelloStatus::Unauthorized
        };
        assert!(matches!(result, Err(status) if status == expected));
    }
    hello.pairing_code = pairing.code();
    assert!(matches!(
        pairing.authorize(PeerId::usb(99), &hello, now),
        Err(HelloStatus::RateLimited)
    ));
    assert!(pairing
        .authorize(PeerId::usb(99), &hello, now + Duration::from_secs(61))
        .is_ok());
}
