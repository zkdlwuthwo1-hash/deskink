use deskink_core::{Board, Brush, Command, Point};

#[test]
fn drawing_a_stroke_retains_its_samples_and_style() {
    let mut board = Board::new();
    let brush = Brush {
        color: [255, 80, 40, 255],
        width: 4.0,
    };
    let start = Point {
        x: 100.0,
        y: 200.0,
        pressure: 0.4,
    };
    let end = Point {
        x: 120.0,
        y: 215.0,
        pressure: 0.8,
    };
    board
        .apply(Command::Begin {
            id: 7,
            brush,
            point: start,
        })
        .unwrap();
    board.apply(Command::Append { id: 7, point: end }).unwrap();
    board.apply(Command::End { id: 7 }).unwrap();

    assert_eq!(board.strokes().len(), 1);
    assert_eq!(board.strokes()[0].points, vec![start, end]);
    assert_eq!(board.strokes()[0].brush, brush);
    assert!(board.strokes()[0].finished);
}
#[test]
fn duplicate_start_cannot_create_a_second_stroke() {
    let mut board = Board::new();
    let brush = Brush {
        color: [255, 80, 40, 255],
        width: 4.0,
    };
    let point = Point {
        x: 100.0,
        y: 200.0,
        pressure: 0.4,
    };
    board
        .apply(Command::Begin {
            id: 7,
            brush,
            point,
        })
        .unwrap();
    assert!(board
        .apply(Command::Begin {
            id: 7,
            brush,
            point
        })
        .is_err());
    assert_eq!(board.strokes().len(), 1);
}
#[test]
fn finished_strokes_cannot_be_extended_by_late_samples() {
    let mut board = Board::new();
    let point = Point {
        x: 100.0,
        y: 200.0,
        pressure: 0.4,
    };
    board
        .apply(Command::Begin {
            id: 7,
            brush: Brush {
                color: [255, 80, 40, 255],
                width: 4.0,
            },
            point,
        })
        .unwrap();
    board.apply(Command::End { id: 7 }).unwrap();
    assert!(board.apply(Command::Append { id: 7, point }).is_err());
    assert_eq!(board.strokes()[0].points.len(), 1);
}
