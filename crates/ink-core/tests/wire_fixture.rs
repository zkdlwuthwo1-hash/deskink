use deskink_core::InkSession;

#[test]
fn shared_ipad_wire_fixture_draws_the_expected_stroke_without_truncating_ids() {
    let messages: Vec<serde_json::Value> =
        serde_json::from_str(include_str!("../../../proto/testdata/deskink-ink.json")).unwrap();
    let mut session = InkSession::new(42, 3, 1920, 1080);
    for message in messages {
        session
            .receive(&serde_json::to_vec(&message).unwrap())
            .unwrap();
    }
    let stroke = &session.board().strokes()[0];
    assert_eq!(stroke.id, u64::MAX);
    assert_eq!(stroke.brush.color, [255, 80, 40, 255]);
    assert_eq!(stroke.points.len(), 2);
    assert_eq!((stroke.points[1].x, stroke.points[1].y), (120.0, 215.0));
    assert!(stroke.finished);
}

#[test]
fn ipad_tool_commands_share_the_windows_wire_contract_and_history() {
    let commands: Vec<serde_json::Value> =
        serde_json::from_str(include_str!("../../../proto/testdata/deskink-tools.json")).unwrap();
    let mut session = InkSession::new(42, 3, 1920, 1080);
    for (index, command) in commands.into_iter().enumerate() {
        let message = serde_json::json!({
            "version": 1, "session": 42, "epoch": 3, "seq": index + 1, "command": command
        });
        session
            .receive(&serde_json::to_vec(&message).unwrap())
            .unwrap();
    }
    assert_eq!(session.board().strokes().len(), 1);
    assert_eq!(session.board().strokes()[0].id, 1);
    assert_eq!(session.board().strokes()[0].brush.color, [255, 210, 35, 80]);
    assert_eq!(session.board().render_strokes().count(), 1);
    assert!(session.board().is_visible());
    assert!(session.board().pointer().is_none());
}
