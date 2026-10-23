import XCTest
@testable import EternalMonitor

final class DeskInkEditingTests: XCTestCase {
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
