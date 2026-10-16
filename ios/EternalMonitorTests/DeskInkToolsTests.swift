import XCTest
@testable import EternalMonitor

final class DeskInkToolsTests: XCTestCase {
    func testEraserSweepAndUndoRestoreTheSameLinesInOrder() {
        let document = DeskInkDocument()
        for (id, y) in [(UInt64(1), CGFloat(100)), (UInt64(2), CGFloat(200)), (UInt64(3), CGFloat(400))] {
            document.apply(.begin(id: id, brush: DeskInkBrush(), point: .init(x: 100, y: y, pressure: 0.5)))
            document.apply(.append(id: id, point: .init(x: 300, y: y, pressure: 0.5)))
            document.apply(.end(id: id))
        }
        document.apply(.eraseBegin(id: 4, point: .init(x: 200, y: 100, pressure: 0.5), radius: 12))
        document.apply(.eraseTo(id: 4, point: .init(x: 200, y: 220, pressure: 0.5)))
        document.apply(.eraseEnd(id: 4))
        XCTAssertEqual(document.strokes.map(\.id), [3])
        document.apply(.undo)
        XCTAssertEqual(document.strokes.map(\.id), [1, 2, 3])
        document.apply(.redo)
        XCTAssertEqual(document.strokes.map(\.id), [3])
    }
}
