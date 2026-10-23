import XCTest
import UIKit
@testable import EternalMonitor

final class DeskInkEditingTests: XCTestCase {
    @MainActor
    func testHoldingThenSelectingMovingAndResizingUsesTheActualCanvasInput() {
        let canvas=InkCanvasView(frame:CGRect(x:0,y:0,width:1000,height:700)), tools=DeskInkTools()
        canvas.tools=tools; tools.canvas=canvas; tools.editingEnabled=true; tools.shapeCorrection=true
        canvas.configure(videoSize:canvas.bounds.size,enabled:true,connectionKey:"editing-test")
        canvas.layoutIfNeeded()
        XCTAssertTrue(canvas.beginStroke(at:CGPoint(x:100,y:100),pressure:1))
        for p in [CGPoint(x:200,y:100),CGPoint(x:200,y:200),CGPoint(x:100,y:200),CGPoint(x:100,y:100)] { canvas.moveStroke(to:p,pressure:1) }
        RunLoop.main.run(until:Date().addingTimeInterval(1.1))
        XCTAssertEqual(canvas.document.strokes.first?.shape?.kind,.rectangle)
        canvas.finish(); tools.select(.lasso)
        XCTAssertTrue(canvas.beginStroke(at:CGPoint(x:150,y:150),pressure:1))
        canvas.moveStroke(to:CGPoint(x:250,y:200),pressure:1); canvas.finish()
        XCTAssertEqual(canvas.document.strokes[0].points[0].x,200)
        XCTAssertEqual(canvas.document.strokes[0].points[0].y,150)
        XCTAssertTrue(canvas.beginStroke(at:CGPoint(x:300,y:250),pressure:1))
        canvas.moveStroke(to:CGPoint(x:350,y:300),pressure:1); canvas.finish()
        XCTAssertEqual(canvas.document.strokes[0].points[2].x,350)
        XCTAssertEqual(canvas.document.strokes[0].points[2].y,300)
        tools.perform(.undo); XCTAssertEqual(canvas.document.strokes[0].points[2].x,300)
        tools.perform(.undo); XCTAssertEqual(canvas.document.strokes[0].points[0].x,100)
    }
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
