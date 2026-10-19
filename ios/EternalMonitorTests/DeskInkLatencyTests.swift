import XCTest
import UIKit
@testable import EternalMonitor

/// CPU time at the real canvas command/render seam, not physical Pencil latency.
/// Run in Release; slow simulator/device results retain their measured values.
final class DeskInkLatencyTests: XCTestCase {
    @MainActor
    func testPointerCommandsStayResponsiveWithOneThousandCompletedStrokes() {
        let document = DeskInkDocument()
        let tools = DeskInkTools()
        let canvas = InkCanvasView(frame: CGRect(x: 0, y: 0, width: 960, height: 540))
        canvas.tools = tools
        canvas.configure(videoSize: CGSize(width: 1920, height: 1080), enabled: true, connectionKey: "probe")
        canvas.layoutIfNeeded()
        let brush = DeskInkBrush()
        var forwarded = 0
        canvas.send = { forwarded += $0.count }
        for count in [1, 100, 1000] {
            while document.strokes.count < count {
                let id = UInt64(document.strokes.count + 1)
                for sample in 0..<300 {
                    let point = DeskInkPoint(x: 40 + CGFloat(sample) * 4,
                                             y: 80 + CGFloat(id % 20) * 8 + CGFloat(sample % 3), pressure: 0.5)
                    XCTAssertTrue(document.apply(sample == 0 ? .begin(id: id, brush: brush, point: point) : .append(id: id, point: point)))
                }
                XCTAssertTrue(document.apply(.end(id: id)))
            }
            canvas.useDocument(document)
            // Warm the actual rendering path after extending an existing board.
            canvas.perform(.pointer(point: nil, color: [255, 80, 40, 255], radius: 5, laser: false))
            var elapsed: [Double] = []
            let sentBefore = forwarded
            for sample in 0..<120 {
                let start = ProcessInfo.processInfo.systemUptime
                canvas.perform(.pointer(point: .init(x: 60 + CGFloat(sample) * 2, y: 300, pressure: 0.5),
                                        color: [255, 80, 40, 255], radius: 5, laser: false))
                elapsed.append((ProcessInfo.processInfo.systemUptime - start) * 1000)
            }
            elapsed.sort()
            print("INK_CANVAS strokes=\(count) p50_ms=\(elapsed[60]) p95_ms=\(elapsed[114]) max_ms=\(elapsed[119])")
            XCTAssertEqual(forwarded - sentBefore, 120)
            XCTAssertEqual(document.strokes.count, count)
            XCTAssertLessThan(elapsed[114], 16.7, "A pointer update must not consume a whole 60Hz frame")
            RunLoop.main.run(until: Date().addingTimeInterval(0.01))
        }
    }
}
