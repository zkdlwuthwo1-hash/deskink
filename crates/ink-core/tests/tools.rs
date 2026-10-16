use deskink_core::InkSession;
use serde_json::{json, Value};

fn send(session: &mut InkSession, command: Value) {
    let message = json!({"version":1,"session":42,"epoch":3,
        "seq":session.accepted_sequence()+1,"command":command});
    session
        .receive(&serde_json::to_vec(&message).unwrap())
        .unwrap();
}

fn line(session: &mut InkSession, id: u64, y: f32) {
    send(
        session,
        json!({"type":"begin","id":id,"brush":{"color":[255,80,40,255],"width":4},"point":{"x":100,"y":y,"pressure":0.5}}),
    );
    send(
        session,
        json!({"type":"append","id":id,"point":{"x":300,"y":y,"pressure":0.5}}),
    );
    send(session, json!({"type":"end","id":id}));
}

#[test]
fn eraser_sweep_hits_between_samples_and_preserves_other_lines() {
    let mut session = InkSession::new(42, 3, 1920, 1080);
    line(&mut session, 1, 100.0);
    line(&mut session, 2, 400.0);
    send(
        &mut session,
        json!({"type":"erase_begin","id":3,"radius":12,"point":{"x":200,"y":20,"pressure":0.5}}),
    );
    send(
        &mut session,
        json!({"type":"erase_to","id":3,"point":{"x":200,"y":200,"pressure":0.5}}),
    );
    send(&mut session, json!({"type":"erase_end","id":3}));
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
fn one_undo_restores_an_entire_eraser_gesture_in_original_order() {
    let mut session = InkSession::new(42, 3, 1920, 1080);
    for (id, y) in [(1, 100.0), (2, 200.0), (3, 400.0)] {
        line(&mut session, id, y);
    }
    send(
        &mut session,
        json!({"type":"erase_begin","id":4,"radius":12,"point":{"x":200,"y":100,"pressure":0.5}}),
    );
    send(
        &mut session,
        json!({"type":"erase_to","id":4,"point":{"x":200,"y":220,"pressure":0.5}}),
    );
    send(&mut session, json!({"type":"erase_end","id":4}));
    send(&mut session, json!({"type":"undo"}));
    assert_eq!(
        session
            .board()
            .strokes()
            .iter()
            .map(|s| s.id)
            .collect::<Vec<_>>(),
        vec![1, 2, 3]
    );
    send(&mut session, json!({"type":"redo"}));
    assert_eq!(
        session
            .board()
            .strokes()
            .iter()
            .map(|s| s.id)
            .collect::<Vec<_>>(),
        vec![3]
    );
}

#[test]
fn clear_is_undoable_and_new_drawing_discards_redo() {
    let mut session = InkSession::new(42, 3, 1920, 1080);
    line(&mut session, 1, 100.0);
    line(&mut session, 2, 200.0);
    send(&mut session, json!({"type":"clear"}));
    assert!(session.board().strokes().is_empty());
    send(&mut session, json!({"type":"undo"}));
    assert_eq!(session.board().strokes().len(), 2);
    send(&mut session, json!({"type":"undo"}));
    assert_eq!(session.board().strokes()[0].id, 1);
    line(&mut session, 3, 300.0);
    send(&mut session, json!({"type":"redo"}));
    assert_eq!(
        session
            .board()
            .strokes()
            .iter()
            .map(|s| s.id)
            .collect::<Vec<_>>(),
        vec![1, 3]
    );
}

#[test]
fn hiding_and_showing_does_not_delete_or_add_history() {
    let mut session = InkSession::new(42, 3, 1920, 1080);
    line(&mut session, 1, 100.0);
    send(&mut session, json!({"type":"set_visible","visible":false}));
    assert_eq!(session.board().strokes().len(), 1);
    assert!(!session.board().is_visible());
    send(&mut session, json!({"type":"set_visible","visible":true}));
    assert!(session.board().is_visible());
    send(&mut session, json!({"type":"undo"}));
    assert!(session.board().strokes().is_empty());
}

#[test]
fn temporary_ink_does_not_enter_persistent_strokes_or_undo_history() {
    let mut session = InkSession::new(42, 3, 1920, 1080);
    line(&mut session, 1, 100.0);
    send(
        &mut session,
        json!({"type":"transient_begin","id":8,"lifetime_ms":2000,"brush":{"color":[255,0,0,255],"width":4},"point":{"x":20,"y":30,"pressure":0.5}}),
    );
    send(
        &mut session,
        json!({"type":"append","id":8,"point":{"x":40,"y":50,"pressure":0.5}}),
    );
    send(&mut session, json!({"type":"end","id":8}));
    assert_eq!(session.board().strokes().len(), 1);
    send(&mut session, json!({"type":"undo"}));
    assert!(session.board().strokes().is_empty());
}

#[test]
fn temporary_ink_expires_without_erasing_the_permanent_board() {
    let mut session = InkSession::new(42, 3, 1920, 1080);
    line(&mut session, 1, 100.0);
    session.advance_time(1000);
    send(
        &mut session,
        json!({"type":"transient_begin","id":8,"lifetime_ms":2000,"brush":{"color":[255,0,0,255],"width":4},"point":{"x":20,"y":30,"pressure":0.5}}),
    );
    send(&mut session, json!({"type":"end","id":8}));
    session.advance_time(2999);
    assert_eq!(session.board().render_strokes().count(), 2);
    assert!(session.advance_time(3000));
    assert_eq!(
        session
            .board()
            .render_strokes()
            .map(|s| s.id)
            .collect::<Vec<_>>(),
        vec![1]
    );
}

#[test]
fn pointer_is_ephemeral_and_never_changes_undo_history() {
    let mut session = InkSession::new(42, 3, 1920, 1080);
    line(&mut session, 1, 100.0);
    send(
        &mut session,
        json!({"type":"pointer","point":{"x":50,"y":60,"pressure":0.5},"color":[255,0,0,255],"radius":8,"laser":true}),
    );
    assert_eq!(session.board().pointer().unwrap().point.x, 50.0);
    send(&mut session, json!({"type":"undo"}));
    assert!(session.board().strokes().is_empty());
    assert!(session.advance_time(750));
    assert!(session.board().pointer().is_none());
}

#[test]
fn malformed_tool_commands_do_not_advance_or_change_the_session() {
    let mut session = InkSession::new(42, 3, 1920, 1080);
    line(&mut session, 1, 100.0);
    for command in [
        json!({"type":"erase_begin","id":3,"radius":0,"point":{"x":100,"y":100,"pressure":0.5}}),
        json!({"type":"erase_begin","id":3,"radius":12,"point":{"x":-1,"y":100,"pressure":0.5}}),
        json!({"type":"pointer","point":{"x":2000,"y":60,"pressure":0.5},"color":[255,0,0,255],"radius":8,"laser":true}),
        json!({"type":"transient_begin","id":8,"lifetime_ms":999999,"brush":{"color":[255,0,0,255],"width":4},"point":{"x":20,"y":30,"pressure":0.5}}),
    ] {
        let seq = session.accepted_sequence();
        let input = json!({"version":1,"session":42,"epoch":3,"seq":seq+1,"command":command});
        assert!(session
            .receive(&serde_json::to_vec(&input).unwrap())
            .is_err());
        assert_eq!(session.accepted_sequence(), seq);
        assert_eq!(session.board().strokes().len(), 1);
        assert!(session.board().pointer().is_none());
    }
}

#[test]
fn duplicate_end_cannot_add_a_second_undo_action() {
    let mut session = InkSession::new(42, 3, 1920, 1080);
    line(&mut session, 1, 100.0);
    let seq = session.accepted_sequence();
    let duplicate =
        json!({"version":1,"session":42,"epoch":3,"seq":seq+1,"command":{"type":"end","id":1}});
    assert!(session
        .receive(&serde_json::to_vec(&duplicate).unwrap())
        .is_err());
    assert_eq!(session.accepted_sequence(), seq);
}
