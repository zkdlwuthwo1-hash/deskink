import XCTest
@testable import EternalMonitor

final class DeskInkEditingTests: XCTestCase {
    func testSelectionPreviewsCommitOneUndoStepAndDoNotAccumulate() throws {
        let document=DeskInkDocument()
        for id: UInt64 in [1,2] {
            document.apply(.begin(id:id,brush:.init(),point:.init(x:CGFloat(id)*100,y:100,pressure:1)))
            document.apply(.end(id:id))
        }
        for json in [
            #"{"type":"edit_begin","id":9}"#,
            #"{"type":"edit_add","id":9,"ids":[1,2]}"#,
            #"{"type":"edit_transform","id":9,"sx":1,"sy":1,"tx":10,"ty":20}"#,
            #"{"type":"edit_transform","id":9,"sx":2,"sy":1.5,"tx":20,"ty":30}"#,
            #"{"type":"edit_end","id":9}"#
        ] { XCTAssertTrue(document.apply(try JSONDecoder().decode(DeskInkCommand.self,from:Data(json.utf8)))) }
        XCTAssertEqual(document.strokes.map { $0.points[0].x },[220,420])
        XCTAssertEqual(document.strokes[0].points[0].y,180)
        document.apply(.undo); XCTAssertEqual(document.strokes.map { $0.points[0].x },[100,200])
        document.apply(.redo); XCTAssertEqual(document.strokes.map { $0.points[0].x },[220,420])
        document.apply(.undo); document.apply(.undo); XCTAssertEqual(document.strokes.count,1)
    }
    func testShapeCorrectionPreservesTheBrushAndUndoRemovesTheWholeShape() throws {
        let document = DeskInkDocument()
        document.apply(.begin(id: 7, brush: .init(color: [255,210,35,80], width: 20), point: .init(x: 98,y: 102,pressure: 0.5)))
        let command = try JSONDecoder().decode(DeskInkCommand.self, from: Data(#"{"type":"shape","id":7,"kind":"rectangle","a":{"x":100,"y":100,"pressure":0.5},"b":{"x":300,"y":300,"pressure":0.5}}"#.utf8))
        XCTAssertTrue(document.apply(command))
        XCTAssertEqual(document.strokes[0].points.map { $0.x }, [100,300,300,100,100])
        XCTAssertEqual(document.strokes[0].brush.color, [255,210,35,80])
        XCTAssertEqual(document.strokes[0].brush.width, 20)
        document.apply(.end(id: 7)); document.apply(.undo)
        XCTAssertTrue(document.strokes.isEmpty)
        document.apply(.redo)
        XCTAssertEqual(document.strokes[0].points.count, 5)
    }
}
