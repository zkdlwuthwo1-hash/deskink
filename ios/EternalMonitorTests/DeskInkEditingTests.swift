import XCTest
import UIKit
@testable import EternalMonitor

final class DeskInkEditingTests: XCTestCase {
    @MainActor
    func testStationaryPenCorrectsALineDespiteSubPixelHoldSamples() {
        let canvas=InkCanvasView(frame:CGRect(x:0,y:0,width:1000,height:700)), tools=DeskInkTools()
        canvas.tools=tools; tools.canvas=canvas; tools.editingEnabled=true; tools.shapeCorrection=true
        canvas.configure(videoSize:canvas.bounds.size,enabled:true,connectionKey:"hold-jitter-test")
        canvas.layoutIfNeeded()
        XCTAssertTrue(canvas.beginStroke(at:CGPoint(x:100,y:200),pressure:1))
        for x in stride(from:120,through:300,by:20) { canvas.moveStroke(to:CGPoint(x:x,y:200),pressure:1) }
        for sample in 0..<120 {
            canvas.moveStroke(to:CGPoint(x:300+(sample%2 == 0 ? 0.4 : -0.4),y:200),pressure:1)
        }
        RunLoop.main.run(until:Date().addingTimeInterval(1.1))
        XCTAssertEqual(canvas.document.strokes.first?.shape?.kind,.line,
                       "A stationary hold must tolerate the Pencil's sub-pixel samples")
        canvas.finish()
    }

    func testFractionalShapeResizingAtEveryEdgeRemainsValidAtWindowsFloatPrecision() {
        let initial=[DeskInkPoint(x:100.1,y:120.1,pressure:1),DeskInkPoint(x:300.2,y:320.2,pressure:1)]
        for (handle,target) in [(CGPoint(x:100.1,y:220.15),CGPoint(x:0,y:220.15)),
                                (CGPoint(x:300.2,y:220.15),CGPoint(x:1920,y:220.15)),
                                (CGPoint(x:200.15,y:120.1),CGPoint(x:200.15,y:0)),
                                (CGPoint(x:200.15,y:320.2),CGPoint(x:200.15,y:1080))] {
            let document=DeskInkDocument()
            document.apply(.begin(id:1,brush:.init(),point:initial[0])); document.apply(.shape(id:1,kind:.rectangle,a:initial[0],b:initial[1])); document.apply(.end(id:1))
            var transforms: [(CGFloat,CGFloat,CGFloat,CGFloat)]=[]
            let selection=DeskInkSelection(document:document,size:CGSize(width:1920,height:1080)) { command,_ in
                if case let .editTransform(_,sx,sy,tx,ty)=command { transforms.append((sx,sy,tx,ty)) }
                document.apply(command)
            }
            selection.begin(.init(x:200,y:200,pressure:1),scale:1,id:8); selection.end()
            selection.begin(.init(x:handle.x,y:handle.y,pressure:1),scale:1,id:9)
            selection.move(.init(x:target.x,y:target.y,pressure:1),now:100); selection.end()
            XCTAssertFalse(transforms.isEmpty)
            for (sx,sy,tx,ty) in transforms {
                for p in initial {
                    let x=Float(p.x)*Float(sx)+Float(tx), y=Float(p.y)*Float(sy)+Float(ty)
                    XCTAssertTrue((0...1920).contains(x),"Windows rejected x=\(x)")
                    XCTAssertTrue((0...1080).contains(y),"Windows rejected y=\(y)")
                }
            }
        }
    }
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
