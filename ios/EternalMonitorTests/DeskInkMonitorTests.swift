import XCTest
import UIKit
@testable import EternalMonitor

final class DeskInkMonitorTests: XCTestCase {
    @MainActor
    func testSwitchingScreensRestoresInkAndUndoWithoutMixingTheNextHost() {
        let documents = DeskInkMonitorDocuments()
        let canvas = InkCanvasView(frame: CGRect(x: 0, y: 0, width: 960, height: 540))
        let tools = DeskInkTools()
        canvas.tools = tools
        canvas.configure(videoSize: CGSize(width: 1920, height: 1080), enabled: true, connectionKey: "first")
        let host = Data(repeating: 7, count: 16)
        canvas.useDocument(documents.select(hostToken: host, monitor: "PC · DISPLAY1"))
        XCTAssertTrue(canvas.beginStroke(at: CGPoint(x: 100, y: 100), pressure: 0.5))
        canvas.finish()
        canvas.useDocument(documents.select(hostToken: host, monitor: "PC · DISPLAY2"))
        XCTAssertTrue(canvas.document.strokes.isEmpty)
        XCTAssertTrue(canvas.beginStroke(at: CGPoint(x: 300, y: 200), pressure: 0.5))
        canvas.finish()
        canvas.perform(.undo)
        XCTAssertTrue(canvas.document.strokes.isEmpty)
        canvas.useDocument(documents.select(hostToken: host, monitor: "PC · DISPLAY1"))
        XCTAssertEqual(canvas.document.strokes.count, 1)
        XCTAssertEqual(canvas.document.strokes.first?.points.first?.x, 200)
        canvas.perform(.undo)
        XCTAssertTrue(canvas.document.strokes.isEmpty)
        canvas.useDocument(documents.select(hostToken: host, monitor: "PC · DISPLAY2"))
        canvas.perform(.redo)
        XCTAssertEqual(canvas.document.strokes.first?.points.first?.x, 600)
        canvas.useDocument(documents.select(hostToken: Data(repeating: 8, count: 16), monitor: "PC · DISPLAY2"))
        XCTAssertTrue(canvas.document.strokes.isEmpty, "A restarted host owns a fresh board")
    }
}
