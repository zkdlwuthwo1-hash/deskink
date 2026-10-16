use deskink_core::InkSession;

#[test]
fn accepted_connection_messages_produce_a_finished_stroke() {
    let mut session = InkSession::new(42, 3, 1920, 1080);
    for bytes in [
        br#"{"version":1,"session":42,"epoch":3,"seq":1,"command":{"type":"begin","id":7,"brush":{"color":[255,80,40,255],"width":4},"point":{"x":100,"y":200,"pressure":0.4}}}"#.as_slice(),
        br#"{"version":1,"session":42,"epoch":3,"seq":2,"command":{"type":"append","id":7,"point":{"x":120,"y":215,"pressure":0.8}}}"#.as_slice(),
        br#"{"version":1,"session":42,"epoch":3,"seq":3,"command":{"type":"end","id":7}}"#.as_slice(),
    ] { session.receive(bytes).unwrap(); }
    let stroke = &session.board().strokes()[0];
    assert_eq!(stroke.points.len(), 2);
    assert_eq!(stroke.points[1].x, 120.0);
    assert!(stroke.finished);
    assert_eq!(session.accepted_sequence(), 3);
}
#[test]
fn foreign_or_stale_connection_messages_do_not_change_the_board() {
    for (version, connection, epoch) in [(2, 42, 3), (1, 41, 3), (1, 42, 2)] {
        let mut session = InkSession::new(42, 3, 1920, 1080);
        let input = format!(
            r#"{{"version":{version},"session":{connection},"epoch":{epoch},"seq":1,"command":{{"type":"begin","id":7,"brush":{{"color":[255,80,40,255],"width":4}},"point":{{"x":100,"y":200,"pressure":0.4}}}}}}"#
        );
        assert!(session.receive(input.as_bytes()).is_err());
        assert!(session.board().strokes().is_empty());
        assert_eq!(session.accepted_sequence(), 0);
    }
}
#[test]
fn replay_is_idempotent_and_a_sequence_gap_requires_the_missing_event() {
    let mut session = InkSession::new(42, 3, 1920, 1080);
    let start = br#"{"version":1,"session":42,"epoch":3,"seq":1,"command":{"type":"begin","id":7,"brush":{"color":[255,80,40,255],"width":4},"point":{"x":100,"y":200,"pressure":0.4}}}"#;
    let sample = br#"{"version":1,"session":42,"epoch":3,"seq":2,"command":{"type":"append","id":7,"point":{"x":120,"y":215,"pressure":0.8}}}"#;
    let end = br#"{"version":1,"session":42,"epoch":3,"seq":3,"command":{"type":"end","id":7}}"#;
    session.receive(start).unwrap();
    session.receive(start).unwrap();
    assert!(session.receive(end).is_err());
    assert_eq!(session.accepted_sequence(), 1);
    session.receive(sample).unwrap();
    session.receive(sample).unwrap();
    session.receive(end).unwrap();
    assert_eq!(session.board().strokes().len(), 1);
    assert_eq!(session.board().strokes()[0].points.len(), 2);
    assert!(session.board().strokes()[0].finished);
}
#[test]
fn invalid_samples_cannot_enter_or_advance_a_session() {
    for point in [
        r#"{"x":1921,"y":100,"pressure":0.5}"#,
        r#"{"x":-1,"y":100,"pressure":0.5}"#,
        r#"{"x":100,"y":1081,"pressure":0.5}"#,
        r#"{"x":100,"y":100,"pressure":1.1}"#,
    ] {
        let mut session = InkSession::new(42, 3, 1920, 1080);
        let input = format!(
            r#"{{"version":1,"session":42,"epoch":3,"seq":1,"command":{{"type":"begin","id":7,"brush":{{"color":[255,80,40,255],"width":4}},"point":{point}}}}}"#
        );
        assert!(session.receive(input.as_bytes()).is_err());
        assert!(session.board().strokes().is_empty());
        assert_eq!(session.accepted_sequence(), 0);
    }
}
#[test]
fn oversized_or_malformed_or_unrenderable_commands_are_rejected() {
    let valid = r#"{"version":1,"session":42,"epoch":3,"seq":1,"command":{"type":"begin","id":7,"brush":{"color":[255,80,40,255],"width":4},"point":{"x":100,"y":200,"pressure":0.4}}}"#;
    for input in [
        format!("{valid}{}", " ".repeat(1500)),
        valid[..80].into(),
        valid.replace("\"width\":4", "\"width\":-1"),
        valid.replace("\"width\":4", "\"width\":10000"),
    ] {
        let mut session = InkSession::new(42, 3, 1920, 1080);
        assert!(session.receive(input.as_bytes()).is_err());
        assert!(session.board().strokes().is_empty());
    }
}
#[test]
fn reconnect_keeps_visible_ink_but_rejects_the_old_connections_packets() {
    let mut session = InkSession::new(42, 3, 1920, 1080);
    session.receive(br#"{"version":1,"session":42,"epoch":3,"seq":1,"command":{"type":"begin","id":7,"brush":{"color":[255,80,40,255],"width":4},"point":{"x":100,"y":200,"pressure":0.4}}}"#).unwrap();
    session.reconnect(43, 4);
    assert_eq!(session.board().strokes().len(), 1);
    assert!(session.board().strokes()[0].finished);
    assert_eq!(session.accepted_sequence(), 0);
    assert!(session
        .receive(br#"{"version":1,"session":42,"epoch":3,"seq":2,"command":{"type":"end","id":7}}"#)
        .is_err());
}
