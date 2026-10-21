use eternal_host::{annotation::AnnotationState, capture::OutputInfo, transport::link::PeerId};
use std::time::Instant;

fn board() -> AnnotationState {
    AnnotationState::new(
        OutputInfo {
            adapter_index: 0,
            output_index: 0,
            device_name: "test".into(),
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

fn input(seq: u64, command: serde_json::Value) -> Vec<u8> {
    serde_json::to_vec(&serde_json::json!({
        "version":1,"session":42,"epoch":3,"seq":seq,"command":command
    }))
    .unwrap()
}

#[test]
fn network_ink_applies_immediately_and_repairs_reordered_or_repeated_events() {
    let board = board();
    let peer = PeerId::udp("192.0.2.2:2345".parse().unwrap());
    let now = Instant::now();
    let first = input(
        1,
        serde_json::json!({"type":"begin","id":7,"brush":{"color":[255,80,40,255],"width":4},"point":{"x":100,"y":200,"pressure":0.4}}),
    );
    let second = input(
        2,
        serde_json::json!({"type":"append","id":7,"point":{"x":120,"y":210,"pressure":0.5}}),
    );
    let ack = board
        .receive_lan(peer, Some(peer), Some(42), 3, &second, now)
        .unwrap();
    assert_eq!(&ack[..12], b"DA02\x2a\0\0\0\x03\0\0\0");
    assert_eq!(&ack[12..], &[0; 8]);
    assert!(board.ink.lock().board().strokes().is_empty());
    let ack = board
        .receive_lan(peer, Some(peer), Some(42), 3, &first, now)
        .unwrap();
    assert_eq!(&ack[12..], &[2, 0, 0, 0, 0, 0, 0, 0]);
    let ink = board.ink.lock();
    assert_eq!(ink.board().strokes()[0].points.len(), 2);
    assert_eq!(ink.board().strokes()[0].points[1].x, 120.0);
    drop(ink);
    assert_eq!(
        board.receive_lan(peer, Some(peer), Some(42), 3, &first, now),
        Some(ack)
    );
    assert_eq!(board.ink.lock().board().strokes()[0].points.len(), 2);
}

#[test]
fn network_ink_cannot_cross_peer_session_epoch_or_size_limits() {
    let board = board();
    let peer = PeerId::udp("192.0.2.2:2345".parse().unwrap());
    let other = PeerId::udp("192.0.2.2:2346".parse().unwrap());
    let now = Instant::now();
    let clear = input(1, serde_json::json!({"type":"clear"}));
    for (source, active, session, epoch) in [
        (other, Some(peer), Some(42), 3),
        (peer, None, Some(42), 3),
        (peer, Some(peer), None, 3),
        (peer, Some(peer), Some(43), 3),
        (peer, Some(peer), Some(42), 4),
        (PeerId::usb(0), Some(PeerId::usb(0)), Some(42), 3),
    ] {
        assert!(board
            .receive_lan(source, active, session, epoch, &clear, now)
            .is_none());
    }
    assert!(board
        .receive_lan(peer, Some(peer), Some(42), 3, &[b' '; 1025], now)
        .is_none());
    assert_eq!(board.ink.lock().accepted_sequence(), 0);
    assert!(board
        .receive_lan(peer, Some(peer), Some(42), 3, &clear, now)
        .is_some());
    assert_eq!(board.ink.lock().accepted_sequence(), 1);
}

#[test]
fn stalled_network_input_is_bounded_and_never_applied_after_its_deadline() {
    let board = board();
    let peer = PeerId::udp("192.0.2.2:2345".parse().unwrap());
    let now = Instant::now();
    let packet = |seq| input(seq, serde_json::json!({"type":"clear"}));
    assert!(board
        .receive_lan(peer, Some(peer), Some(42), 3, &packet(256), now)
        .is_some());
    assert!(board
        .receive_lan(peer, Some(peer), Some(42), 3, &packet(257), now)
        .is_none());
    assert!(board
        .receive_lan(
            peer,
            Some(peer),
            Some(42),
            3,
            &packet(1),
            now + std::time::Duration::from_millis(250)
        )
        .is_none());
    assert_eq!(board.ink.lock().accepted_sequence(), 0);
}
