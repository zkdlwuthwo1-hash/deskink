use deskink_core::InkSession;
use serde_json::{json, Value};

fn apply(session: &mut InkSession, command: Value) {
    let bytes=serde_json::to_vec(&json!({"version":1,"session":42,"epoch":3,"seq":session.accepted_sequence()+1,"command":command})).unwrap();
    session.receive(&bytes).unwrap();
}

#[test]
fn swept_repeated_and_complete_erasing_match_the_shared_tablet_contract() {
    let cases: Value = serde_json::from_str(include_str!(
        "../../../proto/testdata/deskink-partial-cases.json"
    ))
    .unwrap();
    for case in cases.as_array().unwrap() {
        let mut session = InkSession::new(42, 3, 1920, 1080);
        let point = |xy: &Value| json!({"x":xy[0],"y":xy[1],"pressure":1});
        for (i, points) in case["strokes"].as_array().unwrap().iter().enumerate() {
            apply(
                &mut session,
                json!({"type":"begin","id":i+1,"brush":{"color":[255,210,35,80],"width":4},"point":point(&points[0])}),
            );
            for p in points.as_array().unwrap().iter().skip(1) {
                apply(
                    &mut session,
                    json!({"type":"append","id":i+1,"point":point(p)}),
                );
            }
            apply(&mut session, json!({"type":"end","id":i+1}));
        }
        for (i, key) in ["erase", "secondErase"].iter().enumerate() {
            if let Some(path) = case[key].as_array() {
                apply(
                    &mut session,
                    json!({"type":"erase_partial_begin","id":100+i,"radius":10,"point":point(&path[0])}),
                );
                for p in path.iter().skip(1) {
                    apply(
                        &mut session,
                        json!({"type":"erase_to","id":100+i,"point":point(p)}),
                    );
                }
                apply(&mut session, json!({"type":"erase_end","id":100+i}));
            }
        }
        let expected = case["expected"].as_array().unwrap();
        assert_eq!(
            session.board().strokes().len(),
            expected.len(),
            "{}",
            case["name"]
        );
        for (stroke, e) in session.board().strokes().iter().zip(expected) {
            assert_eq!(stroke.id, e["id"].as_u64().unwrap());
            assert_eq!(stroke.brush.color, [255, 210, 35, 80]);
            assert_eq!(stroke.brush.width, 4.);
            assert_eq!(stroke.points.len(), e["points"].as_array().unwrap().len());
            for (p, xy) in stroke.points.iter().zip(e["points"].as_array().unwrap()) {
                assert!((f64::from(p.x) - xy[0].as_f64().unwrap()).abs() < 0.002);
                assert!((f64::from(p.y) - xy[1].as_f64().unwrap()).abs() < 0.002);
            }
            assert_eq!(serde_json::to_value(&stroke.breaks).unwrap(), e["breaks"]);
        }
        apply(&mut session, json!({"type":"undo"}));
        if !case["secondErase"].is_null() {
            apply(&mut session, json!({"type":"undo"}));
        }
        assert_eq!(
            session.board().strokes().len(),
            case["strokes"].as_array().unwrap().len()
        );
        assert!(session
            .board()
            .strokes()
            .iter()
            .all(|s| s.breaks.is_empty()));
    }
}

#[test]
fn partial_erasing_cuts_only_the_touched_middle_and_undo_restores_the_line() {
    let mut session = InkSession::new(42, 3, 1920, 1080);
    apply(
        &mut session,
        json!({"type":"begin","id":1,"brush":{"color":[255,80,40,255],"width":4},"point":{"x":100,"y":100,"pressure":1}}),
    );
    apply(
        &mut session,
        json!({"type":"append","id":1,"point":{"x":300,"y":100,"pressure":1}}),
    );
    apply(&mut session, json!({"type":"end","id":1}));
    apply(
        &mut session,
        json!({"type":"erase_partial_begin","id":2,"radius":10,"point":{"x":200,"y":100,"pressure":1}}),
    );
    apply(&mut session, json!({"type":"erase_end","id":2}));
    let stroke = &session.board().strokes()[0];
    assert_eq!(
        stroke.points.iter().map(|p| p.x).collect::<Vec<_>>(),
        vec![100., 188., 212., 300.]
    );
    apply(&mut session, json!({"type":"undo"}));
    assert_eq!(
        session.board().strokes()[0]
            .points
            .iter()
            .map(|p| p.x)
            .collect::<Vec<_>>(),
        vec![100., 300.]
    );
    apply(&mut session, json!({"type":"redo"}));
    assert_eq!(session.board().strokes()[0].points.len(), 4);
}
