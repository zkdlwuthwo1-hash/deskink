use deskink_core::InkSession;
use serde_json::{json, Value};

fn send(session: &mut InkSession, seq: u64, command: Value) {
    let bytes = serde_json::to_vec(
        &json!({"version":1,"session":42,"epoch":3,"seq":seq,"command":command}),
    )
    .unwrap();
    assert!(bytes.len() <= 1024);
    session.receive(&bytes).unwrap();
}

#[test]
fn deleting_only_the_lasso_selection_is_one_undoable_operation() {
    let mut session = InkSession::new(42, 3, 1920, 1080);
    let mut seq = 0;
    let mut apply = |session: &mut InkSession, command| {
        seq += 1;
        send(session, seq, command);
    };
    for id in 1..=3 {
        apply(
            &mut session,
            json!({"type":"begin","id":id,"brush":{"color":[255,0,0,255],"width":4},"point":{"x":id*100,"y":100,"pressure":1}}),
        );
        apply(&mut session, json!({"type":"end","id":id}));
    }
    apply(&mut session, json!({"type":"edit_begin","id":9}));
    apply(&mut session, json!({"type":"edit_add","id":9,"ids":[1,3]}));
    apply(&mut session, json!({"type":"edit_delete","id":9}));
    apply(&mut session, json!({"type":"edit_end","id":9}));
    assert_eq!(
        session
            .board()
            .strokes()
            .iter()
            .map(|s| s.id)
            .collect::<Vec<_>>(),
        vec![2]
    );
    apply(&mut session, json!({"type":"undo"}));
    assert_eq!(
        session
            .board()
            .strokes()
            .iter()
            .map(|s| s.id)
            .collect::<Vec<_>>(),
        vec![1, 2, 3]
    );
    apply(&mut session, json!({"type":"redo"}));
    assert_eq!(
        session
            .board()
            .strokes()
            .iter()
            .map(|s| s.id)
            .collect::<Vec<_>>(),
        vec![2]
    );
}

#[test]
fn correcting_a_live_rectangle_preserves_brush_and_undo_removes_the_whole_shape() {
    let mut session = InkSession::new(42, 3, 1920, 1080);
    send(
        &mut session,
        1,
        json!({"type":"begin","id":7,"brush":{"color":[255,210,35,80],"width":20},"point":{"x":98,"y":102,"pressure":0.5}}),
    );
    send(
        &mut session,
        2,
        json!({"type":"append","id":7,"point":{"x":300,"y":305,"pressure":0.5}}),
    );
    send(
        &mut session,
        3,
        json!({"type":"shape","id":7,"kind":"rectangle","a":{"x":100,"y":100,"pressure":0.5},"b":{"x":300,"y":300,"pressure":0.5}}),
    );
    let stroke = &session.board().strokes()[0];
    assert_eq!(
        stroke.points.iter().map(|p| (p.x, p.y)).collect::<Vec<_>>(),
        vec![
            (100., 100.),
            (300., 100.),
            (300., 300.),
            (100., 300.),
            (100., 100.)
        ]
    );
    assert_eq!(stroke.brush.color, [255, 210, 35, 80]);
    assert_eq!(stroke.brush.width, 20.);
    send(&mut session, 4, json!({"type":"end","id":7}));
    send(&mut session, 5, json!({"type":"undo"}));
    assert!(session.board().strokes().is_empty());
    send(&mut session, 6, json!({"type":"redo"}));
    assert_eq!(session.board().strokes()[0].points.len(), 5);
}

#[test]
fn ellipse_and_line_correction_use_the_requested_bounds() {
    for kind in ["ellipse", "line"] {
        let mut session = InkSession::new(42, 3, 1920, 1080);
        send(
            &mut session,
            1,
            json!({"type":"begin","id":7,"brush":{"color":[35,125,255,255],"width":4},"point":{"x":100,"y":100,"pressure":0.5}}),
        );
        send(
            &mut session,
            2,
            json!({"type":"shape","id":7,"kind":kind,"a":{"x":100,"y":100,"pressure":0.5},"b":{"x":300,"y":200,"pressure":0.5}}),
        );
        let points = &session.board().strokes()[0].points;
        if kind == "line" {
            assert_eq!(points.len(), 2);
            assert_eq!((points[1].x, points[1].y), (300., 200.));
        } else {
            assert_eq!(points.len(), 65);
            assert_eq!((points[0].x, points[0].y), (300., 150.));
            assert!((points[16].x - 200.).abs() < 0.01 && (points[16].y - 200.).abs() < 0.01);
            assert_eq!(points.first(), points.last());
        }
    }
}

#[test]
fn moving_and_resizing_a_group_is_one_undoable_edit_without_accumulating_previews() {
    let mut session = InkSession::new(42, 3, 1920, 1080);
    let mut seq = 0;
    let mut apply = |session: &mut InkSession, command| {
        seq += 1;
        send(session, seq, command);
    };
    for (id, x) in [(1, 100), (2, 200)] {
        apply(
            &mut session,
            json!({"type":"begin","id":id,"brush":{"color":[255,80,40,255],"width":4},"point":{"x":x,"y":100,"pressure":0.5}}),
        );
        apply(&mut session, json!({"type":"end","id":id}));
    }
    apply(&mut session, json!({"type":"edit_begin","id":9}));
    apply(&mut session, json!({"type":"edit_add","id":9,"ids":[1,2]}));
    apply(
        &mut session,
        json!({"type":"edit_transform","id":9,"sx":1,"sy":1,"tx":10,"ty":20}),
    );
    apply(
        &mut session,
        json!({"type":"edit_transform","id":9,"sx":2,"sy":1.5,"tx":20,"ty":30}),
    );
    apply(&mut session, json!({"type":"edit_end","id":9}));
    assert_eq!(
        session
            .board()
            .strokes()
            .iter()
            .map(|s| (s.points[0].x, s.points[0].y, s.brush.width))
            .collect::<Vec<_>>(),
        vec![(220., 180., 4.), (420., 180., 4.)]
    );
    apply(&mut session, json!({"type":"undo"}));
    assert_eq!(
        session
            .board()
            .strokes()
            .iter()
            .map(|s| s.points[0].x)
            .collect::<Vec<_>>(),
        vec![100., 200.]
    );
    apply(&mut session, json!({"type":"redo"}));
    assert_eq!(
        session
            .board()
            .strokes()
            .iter()
            .map(|s| s.points[0].x)
            .collect::<Vec<_>>(),
        vec![220., 420.]
    );
}

#[test]
fn invalid_transform_does_not_advance_or_mutate_and_reconnect_keeps_the_edit_undoable() {
    let mut session = InkSession::new(42, 3, 1920, 1080);
    send(
        &mut session,
        1,
        json!({"type":"begin","id":1,"brush":{"color":[255,80,40,255],"width":4},"point":{"x":100,"y":100,"pressure":1}}),
    );
    send(&mut session, 2, json!({"type":"end","id":1}));
    send(&mut session, 3, json!({"type":"edit_begin","id":9}));
    send(&mut session, 4, json!({"type":"edit_add","id":9,"ids":[1]}));
    for command in [
        json!({"type":"edit_transform","id":9,"sx":1,"sy":1,"tx":2000,"ty":0}),
        json!({"type":"edit_transform","id":9,"sx":-1,"sy":1,"tx":0,"ty":0}),
        json!({"type":"clear"}),
    ] {
        let bytes = serde_json::to_vec(
            &json!({"version":1,"session":42,"epoch":3,"seq":5,"command":command}),
        )
        .unwrap();
        assert!(session.receive(&bytes).is_err());
        assert_eq!(session.accepted_sequence(), 4);
        assert_eq!(session.board().strokes()[0].points[0].x, 100.);
    }
    send(
        &mut session,
        5,
        json!({"type":"edit_transform","id":9,"sx":1,"sy":1,"tx":20,"ty":0}),
    );
    session.reconnect(43, 4);
    session
        .receive(br#"{"version":1,"session":43,"epoch":4,"seq":1,"command":{"type":"undo"}}"#)
        .unwrap();
    assert_eq!(session.board().strokes()[0].points[0].x, 100.);
}
