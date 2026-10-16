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
