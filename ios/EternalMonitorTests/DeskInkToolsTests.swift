import XCTest
import SwiftUI
@testable import EternalMonitor

final class DeskInkToolsTests: XCTestCase {
    @MainActor
    func testEraserSizesSurviveRelaunchAndControlTheActualStrokeAndPointer() throws {
        let suite = "DeskInkToolsTests-\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }
        for (size, radius) in [(DeskInkEraserSize.small, CGFloat(12)), (.medium, CGFloat(24)), (.large, CGFloat(48))] {
            let settings = DeskInkTools(defaults: defaults)
            settings.eraserSize = size
            let tools = DeskInkTools(defaults: defaults), canvas = InkCanvasView()
            tools.canvas = canvas; canvas.tools = tools
            canvas.frame = CGRect(x: 0, y: 0, width: 1920, height: 1080)
            canvas.configure(videoSize: canvas.bounds.size, enabled: true, connectionKey: "eraser")
            tools.width = 8; tools.select(.highlighter); tools.toggleEraser()
            var sent: [DeskInkCommand] = []; canvas.send = { sent += $0 }
            XCTAssertTrue(canvas.beginStroke(at: CGPoint(x: 500, y: 350), pressure: 1))
            guard case let .eraseBegin(_, _, actual) = sent.first else { return XCTFail("Expected erase gesture") }
            XCTAssertEqual(actual, radius)
            XCTAssertEqual(canvas.document.pointer?.radius, radius)
            canvas.finish()
            XCTAssertEqual(tools.tool, .highlighter)
        }
    }
    @MainActor
    func testLiftingAfterErasingRestoresThePreviousPen() {
        let tools = DeskInkTools()
        tools.select(.eraser)
        tools.eraserGestureDidEnd()
        XCTAssertEqual(tools.tool, .pen)

        tools.select(.highlighter)
        tools.toggleEraser()
        tools.eraserGestureDidEnd()
        XCTAssertEqual(tools.tool, .highlighter)

        tools.select(.vanishing)
        tools.select(.eraser)
        tools.eraserGestureDidEnd()
        XCTAssertEqual(tools.tool, .vanishing)
    }

    func testToolWireContractMatchesWindowsAndPreservesHistory() throws {
        let url = try XCTUnwrap(Bundle(for: Self.self).url(forResource: "deskink-tools", withExtension: "json", subdirectory: "testdata"))
        let data = try Data(contentsOf: url)
        let commands = try JSONDecoder().decode([DeskInkCommand].self, from: data)
        let original = try JSONSerialization.jsonObject(with: data) as! NSArray
        let encoded = try JSONSerialization.jsonObject(with: JSONEncoder().encode(commands)) as! NSArray
        XCTAssertEqual(encoded, original)
        let document = DeskInkDocument()
        for command in commands { XCTAssertTrue(document.apply(command)) }
        XCTAssertEqual(document.strokes.map(\.id), [1])
        XCTAssertEqual(document.strokes.first?.brush.color, [255, 210, 35, 80])
        XCTAssertEqual(document.renderStrokes.count, 1)
        XCTAssertTrue(document.visible)
        XCTAssertNil(document.pointer)
    }

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

    func testTemporaryInkExpiresAndNeverEntersUndoHistory() {
        let document = DeskInkDocument()
        document.advanceTime(1000)
        document.apply(.begin(id: 1, brush: DeskInkBrush(), point: .init(x:100,y:100,pressure:0.5)))
        document.apply(.end(id:1))
        document.apply(.transientBegin(id: 8, brush: DeskInkBrush(), point: .init(x:20,y:30,pressure:0.5), lifetimeMs:2000))
        document.apply(.end(id:8))
        document.advanceTime(2999)
        XCTAssertEqual(document.renderStrokes.count,2)
        document.advanceTime(3000)
        XCTAssertEqual(document.renderStrokes.map(\.id),[1])
        document.apply(.undo)
        XCTAssertTrue(document.strokes.isEmpty)
    }

    func testClearHideAndRedoPreserveDrawing() {
        let document = DeskInkDocument()
        document.apply(.begin(id:1,brush:DeskInkBrush(color:[255,210,35,80],width:20),point:.init(x:10,y:20,pressure:0.5)))
        document.apply(.end(id:1))
        document.apply(.setVisible(false))
        XCTAssertTrue(document.renderStrokes.isEmpty)
        XCTAssertEqual(document.strokes.count,1)
        document.apply(.setVisible(true))
        document.apply(.clear)
        XCTAssertTrue(document.strokes.isEmpty)
        document.apply(.undo)
        XCTAssertEqual(document.strokes.first?.brush.color,[255,210,35,80])
        document.apply(.redo)
        XCTAssertTrue(document.strokes.isEmpty)
    }

    @MainActor
    func testDoubleTapRestoresPreviousToolAndToolbarSendsCommands() {
        let tools = DeskInkTools(), canvas = InkCanvasView()
        tools.canvas = canvas; canvas.tools = tools
        canvas.configure(videoSize:CGSize(width:1920,height:1080),enabled:true,connectionKey:"42:3")
        tools.select(.highlighter)
        canvas.pencilInteractionDidTap(UIPencilInteraction())
        XCTAssertEqual(tools.tool,.eraser)
        canvas.pencilInteractionDidTap(UIPencilInteraction())
        XCTAssertEqual(tools.tool,.highlighter)
        var sent: [DeskInkCommand] = []
        canvas.send = { sent += $0 }
        tools.perform(.setVisible(false))
        XCTAssertFalse(canvas.document.visible)
        XCTAssertEqual(sent.count,1)
    }

    @MainActor
    func testToolbarRendersInBothIPadOrientations() throws {
        for (name,size) in [("landscape",CGSize(width:1024,height:768)),("portrait",CGSize(width:768,height:1024))] {
            let tools = DeskInkTools()
            tools.canUndo = true; tools.editingEnabled = true
            let host = UIHostingController(rootView: ZStack {
                UIPreview.Backdrop()
                VStack { Spacer(); DeskInkToolbar(tools:tools).padding(16) }
            })
            let window = UIWindow(frame:CGRect(origin:.zero,size:size))
            window.rootViewController = host; window.makeKeyAndVisible()
            host.view.frame = CGRect(origin:.zero,size:size)
            host.view.setNeedsLayout(); host.view.layoutIfNeeded()
            RunLoop.main.run(until:Date().addingTimeInterval(0.15))
            let image = UIGraphicsImageRenderer(size:size).image { _ in
                host.view.drawHierarchy(in:CGRect(origin:.zero,size:size),afterScreenUpdates:true)
            }
            let png = try XCTUnwrap(image.pngData())
            try png.write(to:URL(fileURLWithPath:NSTemporaryDirectory()).appendingPathComponent("deskink-toolbar-\(name).png"))
            let attachment = XCTAttachment(image:image); attachment.lifetime = .keepAlways; add(attachment)
            window.isHidden = true
        }
    }
}
