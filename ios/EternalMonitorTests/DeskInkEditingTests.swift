import XCTest
import UIKit
@testable import EternalMonitor

final class DeskInkEditingTests: XCTestCase {
    func testSweptAndRepeatedErasingMatchesTheWindowsAndAndroidContract() throws {
        struct Expected:Decodable { let id:UInt64; let points:[[CGFloat]]; let breaks:[Int] }
        struct Fixture:Decodable { let name:String; let strokes:[[[CGFloat]]]; let erase:[[CGFloat]]; let secondErase:[[CGFloat]]?; let expected:[Expected] }
        let url=try XCTUnwrap(Bundle(for:Self.self).url(forResource:"deskink-partial-cases",withExtension:"json",subdirectory:"testdata"))
        let fixtures=try JSONDecoder().decode([Fixture].self,from:Data(contentsOf:url))
        for fixture in fixtures {
            let d=DeskInkDocument()
            func point(_ xy:[CGFloat])->DeskInkPoint { .init(x:xy[0],y:xy[1],pressure:1) }
            for (i,points) in fixture.strokes.enumerated() {
                let id=UInt64(i+1)
                d.apply(.begin(id:id,brush:DeskInkBrush(color:[255,210,35,80],width:4),point:point(points[0])))
                for p in points.dropFirst() { d.apply(.append(id:id,point:point(p))) }; d.apply(.end(id:id))
            }
            for (i,path) in ([fixture.erase]+(fixture.secondErase.map { [$0] } ?? [])).enumerated() {
                let id=UInt64(100+i)
                d.apply(.erasePartialBegin(id:id,point:point(path[0]),radius:10))
                for p in path.dropFirst() { d.apply(.eraseTo(id:id,point:point(p))) }; d.apply(.eraseEnd(id:id))
            }
            XCTAssertEqual(d.strokes.count,fixture.expected.count,fixture.name)
            for (actual,expected) in zip(d.strokes,fixture.expected) {
                XCTAssertEqual(actual.id,expected.id); XCTAssertEqual(actual.brush.color,[255,210,35,80]); XCTAssertEqual(actual.brush.width,4)
                XCTAssertEqual(actual.points.count,expected.points.count)
                for (p,xy) in zip(actual.points,expected.points) { XCTAssertEqual(p.x,xy[0],accuracy:0.002); XCTAssertEqual(p.y,xy[1],accuracy:0.002) }
                XCTAssertEqual(actual.breaks,Set(expected.breaks))
            }
            d.apply(.undo); if fixture.secondErase != nil { d.apply(.undo) }
            XCTAssertEqual(d.strokes.count,fixture.strokes.count); XCTAssertTrue(d.strokes.allSatisfy { $0.breaks.isEmpty })
        }
    }
    @MainActor
    func testSelectionShowsResizeHandlesAndDeleteButtonThenUndoRestoresIt() throws {
        let host=UIViewController(),window=UIWindow(frame:CGRect(x:0,y:0,width:1000,height:700))
        let canvas=InkCanvasView(frame:window.bounds),tools=DeskInkTools()
        host.view=canvas; window.rootViewController=host; window.makeKeyAndVisible()
        defer { window.isHidden=true; window.rootViewController=nil }
        canvas.backgroundColor = UIColor(red:0.06,green:0.07,blue:0.08,alpha:1)
        canvas.tools=tools; tools.canvas=canvas; tools.editingEnabled=true; tools.erasingEnabled=true
        canvas.configure(videoSize:canvas.bounds.size,enabled:true,connectionKey:"selection")
        let d=DeskInkDocument()
        d.apply(.begin(id:1,brush:DeskInkBrush(),point:.init(x:100,y:100,pressure:1)))
        d.apply(.shape(id:1,kind:.rectangle,a:.init(x:100,y:100,pressure:1),b:.init(x:300,y:300,pressure:1))); d.apply(.end(id:1))
        d.apply(.begin(id:2,brush:DeskInkBrush(),point:.init(x:600,y:500,pressure:1))); d.apply(.end(id:2))
        canvas.useDocument(d); canvas.layoutIfNeeded(); tools.select(.lasso)
        XCTAssertTrue(canvas.beginStroke(at:CGPoint(x:200,y:200),pressure:1)); canvas.finish()
        let button=try XCTUnwrap(canvas.subviews.compactMap { $0 as? UIButton }.first { $0.accessibilityIdentifier=="deskink.selection.delete" })
        XCTAssertFalse(button.isHidden); XCTAssertTrue(button.isEnabled)
        XCTAssertTrue(canvas.beginStroke(at:CGPoint(x:300,y:300),pressure:1))
        canvas.moveStroke(to:CGPoint(x:400,y:400),pressure:1); canvas.finish()
        XCTAssertEqual(d.strokes[0].points[2].x,400,accuracy:0.01)
        let image=UIGraphicsImageRenderer(size:canvas.bounds.size).image { context in canvas.layer.render(in:context.cgContext) }
        let attachment=XCTAttachment(image:image); attachment.lifetime = .keepAlways; add(attachment)
        try image.pngData()?.write(to:URL(fileURLWithPath:NSTemporaryDirectory()).appendingPathComponent("deskink-selection-064.png"))
        button.sendActions(for:.touchUpInside)
        XCTAssertEqual(d.strokes.map(\.id),[2]); XCTAssertTrue(button.isHidden)
        canvas.perform(.undo); XCTAssertEqual(d.strokes.map(\.id),[1,2]); XCTAssertEqual(d.strokes[0].points[2].x,400,accuracy:0.01)
        canvas.perform(.undo); XCTAssertEqual(d.strokes[0].points[2].x,300,accuracy:0.01)
    }

    @MainActor
    func testPartialEraserModeIsRememberedAndOldHostsNeverReceiveIt() throws {
        let suite="partial-eraser-\(UUID())", defaults=try XCTUnwrap(UserDefaults(suiteName:suite))
        defer { defaults.removePersistentDomain(forName:suite) }
        let saved=DeskInkTools(defaults:defaults); saved.eraserMode = .partial; saved.eraserSize = .medium
        let tools=DeskInkTools(defaults:defaults),canvas=InkCanvasView(frame:CGRect(x:0,y:0,width:1000,height:700))
        tools.canvas=canvas; canvas.tools=tools; canvas.configure(videoSize:canvas.bounds.size,enabled:true,connectionKey:"eraser")
        var sent:[DeskInkCommand]=[]; canvas.send={ sent += $0 }
        tools.select(.eraser)
        XCTAssertFalse(canvas.beginStroke(at:CGPoint(x:200,y:100),pressure:1)); XCTAssertTrue(sent.isEmpty)
        tools.erasingEnabled=true
        XCTAssertTrue(canvas.beginStroke(at:CGPoint(x:200,y:100),pressure:1))
        guard case let .erasePartialBegin(_,_,radius)=sent.first else { return XCTFail("Expected partial eraser command") }
        XCTAssertEqual(radius,24); canvas.finish(); XCTAssertEqual(tools.tool,.pen)
    }
    func testPartialErasingCutsTheMiddleAndUndoRestoresTheLine() throws {
        let d=DeskInkDocument()
        d.apply(.begin(id:1,brush:DeskInkBrush(),point:.init(x:100,y:100,pressure:1)))
        d.apply(.append(id:1,point:.init(x:300,y:100,pressure:1))); d.apply(.end(id:1))
        let raw=Data(#"{"type":"erase_partial_begin","id":2,"radius":10,"point":{"x":200,"y":100,"pressure":1}}"#.utf8)
        d.apply(try JSONDecoder().decode(DeskInkCommand.self,from:raw)); d.apply(.eraseEnd(id:2))
        XCTAssertEqual(d.strokes.first?.points.map(\.x),[100,188,212,300])
        d.apply(.undo); XCTAssertEqual(d.strokes.first?.points.map(\.x),[100,300])
        d.apply(.redo); XCTAssertEqual(d.strokes.first?.points.map(\.x),[100,188,212,300])
    }
    func testTiltedAndRoundedBoxesStayDistinctFromCirclesOvalsAndHandwriting() throws {
        struct Fixture: Decodable {
            let name: String; let expected: String?; let points: [[CGFloat]]
            let allowUnrecognizedWhenTilted: Bool?
        }
        let url=try XCTUnwrap(Bundle(for:Self.self).url(forResource:"deskink-shape-cases",withExtension:"json",subdirectory:"testdata"))
        let fixtures=try JSONDecoder().decode([Fixture].self,from:Data(contentsOf:url))
        for fixture in fixtures {
            for degrees in [-15,-10,0,10,15] { for scale: CGFloat in [0.5,1,3] {
                let angle=CGFloat(degrees) * .pi/180, c=cos(angle), s=sin(angle)
                let points=fixture.points.map { xy -> DeskInkPoint in
                    let x=xy[0]-200, y=xy[1]-200
                    return .init(x:600+scale*(x*c-y*s),y:600+scale*(x*s+y*c),pressure:0.5)
                }
                let shape=DeskInkShape.recognize(points)
                // Existing unsupported tilted ovals may retain their ink, but
                // widening rectangle recognition must not turn them into boxes.
                if degrees != 0, fixture.allowUnrecognizedWhenTilted==true, shape==nil { continue }
                XCTAssertEqual(shape?.kind.rawValue,fixture.expected,
                               "\(fixture.name) at \(degrees) degrees, scale \(scale)")
            } }
        }
    }

    @MainActor
    func testSlightlyTiltedRectangleMustNotBecomeAnEllipse() {
        let canvas=InkCanvasView(frame:CGRect(x:0,y:0,width:1000,height:700)), tools=DeskInkTools()
        canvas.tools=tools; tools.canvas=canvas; tools.editingEnabled=true; tools.shapeCorrection=true
        canvas.configure(videoSize:canvas.bounds.size,enabled:true,connectionKey:"tilted-rectangle")
        canvas.layoutIfNeeded()
        // A 200-point square tilted ten degrees, not an oval.
        XCTAssertTrue(canvas.beginStroke(at:CGPoint(x:168.88,y:134.15),pressure:1))
        for p in [CGPoint(x:365.85,y:168.88),CGPoint(x:331.12,y:365.85),
                  CGPoint(x:134.15,y:331.12),CGPoint(x:168.88,y:134.15)] {
            canvas.moveStroke(to:p,pressure:1)
        }
        RunLoop.main.run(until:Date().addingTimeInterval(0.6))
        XCTAssertEqual(canvas.document.strokes.first?.shape?.kind,.rectangle,
                       "A slightly tilted box must stay a rectangle")
        XCTAssertEqual(canvas.document.strokes.first?.finished,false,"The shape must change before lifting the pen")
        canvas.finish()
    }

    @MainActor
    func testSmallHandTremorDoesNotRestartTheHalfSecondHold() {
        let canvas=InkCanvasView(frame:CGRect(x:0,y:0,width:1000,height:700)), tools=DeskInkTools()
        canvas.tools=tools; tools.canvas=canvas; tools.editingEnabled=true; tools.shapeCorrection=true
        canvas.configure(videoSize:canvas.bounds.size,enabled:true,connectionKey:"hold-hand-tremor")
        canvas.layoutIfNeeded()
        XCTAssertTrue(canvas.beginStroke(at:CGPoint(x:100,y:200),pressure:1))
        canvas.moveStroke(to:CGPoint(x:300,y:200),pressure:1)
        let pauseStarted=Date()
        for sample in 0..<4 {
            RunLoop.main.run(until:pauseStarted.addingTimeInterval(Double(sample+1)*0.08))
            canvas.moveStroke(to:CGPoint(x:300+(sample%2 == 0 ? 4 : -4),y:200),pressure:1)
        }
        XCTAssertNil(canvas.document.strokes.first?.shape)
        RunLoop.main.run(until:pauseStarted.addingTimeInterval(0.6))
        XCTAssertEqual(canvas.document.strokes.first?.shape?.kind,.line,
                       "A small hand tremor must not extend the half-second hold indefinitely")
        canvas.finish()
    }

    @MainActor
    func testPencilRectangleHoldAndNextStrokeThroughUIKitTouchCallbacks() throws {
        let viewport=DeskInkViewportView(videoView:UIView())
        let host=UIViewController(), window=UIWindow(frame:CGRect(x:0,y:0,width:1000,height:700))
        host.view=viewport; window.rootViewController=host; window.makeKeyAndVisible()
        defer { viewport.canvas.finish(); window.isHidden=true; window.rootViewController=nil }
        let tools=DeskInkTools(); tools.editingEnabled=true; tools.shapeCorrection=true
        viewport.configure(videoSize:CGSize(width:1000,height:700),enabled:true,
                           connectionKey:"rectangle-pencil-touch",tools:tools,send:{ _ in })
        viewport.layoutIfNeeded()
        let canvas=viewport.canvas, pen=RectanglePencilTouch()
        pen.position=CGPoint(x:140,y:140)
        canvas.touchesBegan([pen],with:nil)
        XCTAssertFalse(viewport.panGestureRecognizer.isEnabled,"A live Pencil contact must suppress finger panning")
        for point in [CGPoint(x:240,y:138),CGPoint(x:340,y:142),CGPoint(x:350,y:145),
                      CGPoint(x:352,y:245),CGPoint(x:348,y:340),CGPoint(x:345,y:350),
                      CGPoint(x:245,y:355),CGPoint(x:145,y:349),CGPoint(x:135,y:345),
                      CGPoint(x:138,y:245),CGPoint(x:141,y:145)] {
            pen.position=point; pen.sampleTime += 0.016
            canvas.touchesMoved([pen],with:nil)
        }
        let pauseStarted=Date()
        RunLoop.main.run(until:pauseStarted.addingTimeInterval(0.3))
        XCTAssertNil(canvas.document.strokes.first?.shape,"A brief pause must retain ordinary ink")
        RunLoop.main.run(until:pauseStarted.addingTimeInterval(0.6))
        XCTAssertEqual(canvas.document.strokes.first?.shape?.kind,.rectangle)
        XCTAssertEqual(canvas.document.strokes.first?.finished,false,"Correction must happen before lifting")
        XCTAssertEqual(tools.tool,.pen)
        XCTAssertFalse(viewport.panGestureRecognizer.isEnabled)
        pen.sampleTime += 0.6; canvas.touchesEnded([pen],with:nil)
        XCTAssertTrue(viewport.panGestureRecognizer.isEnabled)

        let next=RectanglePencilTouch(); next.position=CGPoint(x:400,y:400)
        canvas.touchesBegan([next],with:nil)
        next.position=CGPoint(x:500,y:400); next.sampleTime += 0.016
        canvas.touchesMoved([next],with:nil); canvas.touchesEnded([next],with:nil)
        XCTAssertEqual(canvas.document.strokes.count,2,"The next Pencil contact must still draw after a shape hold")
        XCTAssertGreaterThan(canvas.document.strokes.last?.points.count ?? 0,1)
        XCTAssertEqual(tools.tool,.pen)
    }

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
        RunLoop.main.run(until:Date().addingTimeInterval(0.6))
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
        RunLoop.main.run(until:Date().addingTimeInterval(0.6))
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

private final class RectanglePencilTouch: UITouch {
    var sampleTime: TimeInterval=1
    var position=CGPoint.zero
    override var type: UITouch.TouchType { .pencil }
    override var timestamp: TimeInterval { sampleTime }
    override var force: CGFloat { 2 }
    override var maximumPossibleForce: CGFloat { 4 }
    override func location(in view:UIView?) -> CGPoint { position }
    override func preciseLocation(in view:UIView?) -> CGPoint { position }
}
