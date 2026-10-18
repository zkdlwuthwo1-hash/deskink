import XCTest
import UIKit
import SwiftUI
@testable import EternalMonitor

final class DeskInkViewportTests: XCTestCase {
    @MainActor
    func testAirLandscapeWorkspaceUsesBothScreenEdgesWithoutCroppingDesktop() throws {
        let size = CGSize(width: 1180, height: 820) // iPad Air 10.9-inch landscape points
        let host = UIHostingController(rootView: UIPreview.DeskInkGesturePreview())
        let window = UIWindow(frame: CGRect(origin: .zero, size: size))
        window.rootViewController = host; window.makeKeyAndVisible()
        defer { window.isHidden = true }
        host.view.frame = window.bounds
        host.view.setNeedsLayout(); host.view.layoutIfNeeded()
        RunLoop.main.run(until: Date().addingTimeInterval(0.2))
        func viewport(in view: UIView) -> DeskInkViewportView? {
            if let result = view as? DeskInkViewportView { return result }
            return view.subviews.lazy.compactMap { viewport(in: $0) }.first
        }
        let surface = try XCTUnwrap(viewport(in: host.view))
        XCTAssertEqual(surface.bounds.width, 1180, accuracy: 1)
        // These are actual Pencil entry points. A pillarbox must reject them,
        // while a full-width, uncropped desktop maps them to its two edges.
        for x in [CGFloat(0.5), surface.canvas.bounds.width - 0.5] {
            XCTAssertTrue(surface.canvas.beginStroke(at: CGPoint(x: x, y: surface.canvas.bounds.midY), pressure: 0.5),
                          "The fitted desktop must reach both sides on iPad Air")
            surface.canvas.finish()
        }
        let points = surface.canvas.document.strokes.compactMap { $0.points.first }
        XCTAssertEqual(points.count, 2)
        if points.count == 2 {
            XCTAssertEqual(points[0].x, 0, accuracy: 1)
            XCTAssertEqual(points[1].x, 1920, accuracy: 1)
        }
        let image = UIGraphicsImageRenderer(size: size).image { _ in
            host.view.drawHierarchy(in: window.bounds, afterScreenUpdates: true)
        }
        let attachment = XCTAttachment(image: image)
        attachment.name = "deskink-air-full-width"; attachment.lifetime = .keepAlways
        add(attachment)
    }

    @MainActor
    func testFitAndRotationKeepInkAndRejectLetterboxInput() throws {
        let viewport = DeskInkViewportView(videoView: UIView())
        viewport.frame = CGRect(x: 0, y: 0, width: 960, height: 540)
        let tools = DeskInkTools()
        var sent: [DeskInkCommand] = []
        viewport.configure(videoSize: CGSize(width: 1920, height: 1080), enabled: true,
                           connectionKey: "42:3", tools: tools, send: { sent += $0 })
        viewport.layoutIfNeeded()
        XCTAssertTrue(viewport.canvas.beginStroke(at: CGPoint(x: 480, y: 270), pressure: 0.5))
        viewport.canvas.finish()
        viewport.setZoomScale(3, animated: false)
        viewport.setContentOffset(CGPoint(x: 600, y: 400), animated: false)
        tools.resetZoom()
        XCTAssertEqual(viewport.zoomScale, 1)
        XCTAssertEqual(viewport.contentOffset, .zero)
        XCTAssertEqual(viewport.canvas.document.strokes.count, 1)

        viewport.frame = CGRect(x: 0, y: 0, width: 540, height: 960)
        viewport.layoutIfNeeded()
        sent.removeAll()
        XCTAssertFalse(viewport.canvas.beginStroke(at: CGPoint(x: 270, y: 100), pressure: 0.5))
        XCTAssertTrue(sent.isEmpty)
        XCTAssertTrue(viewport.canvas.beginStroke(at: CGPoint(x: 270, y: 480), pressure: 0.5))
        viewport.canvas.finish()
        guard case let .begin(_, _, point) = try XCTUnwrap(sent.first) else { return XCTFail("Expected stroke") }
        XCTAssertEqual(point.x, 960, accuracy: 0.01)
        XCTAssertEqual(point.y, 540, accuracy: 0.01)
        XCTAssertEqual(viewport.canvas.document.strokes.count, 2)
    }

    @MainActor
    func testZoomAndPanKeepVideoAndSentInkInWindowsCoordinates() throws {
        let video = UIView()
        let viewport = DeskInkViewportView(videoView: video)
        let screen = UIView(frame: CGRect(x: 0, y: 0, width: 960, height: 540))
        viewport.frame = screen.bounds
        screen.addSubview(viewport)
        let tools = DeskInkTools()
        var sent: [DeskInkCommand] = []
        viewport.configure(videoSize: CGSize(width: 1920, height: 1080), enabled: true,
                           connectionKey: "42:3", tools: tools, send: { sent += $0 })
        viewport.layoutIfNeeded()
        viewport.setZoomScale(2, animated: false)
        viewport.setContentOffset(CGPoint(x: 480, y: 270), animated: false)
        viewport.layoutIfNeeded()
        XCTAssertTrue(sent.isEmpty, "Looking closer must not send ink or change the PC")

        // At 2x with this pan, viewport (240,135) is desktop (720,405).
        // Use the containing screen: a UIScrollView's own coordinates already
        // include its bounds origin/content offset.
        let location = viewport.canvas.convert(CGPoint(x: 240, y: 135), from: screen)
        XCTAssertTrue(viewport.canvas.beginStroke(at: location, pressure: 0.5))
        viewport.canvas.finish()
        guard case let .begin(_, _, point) = try XCTUnwrap(sent.first) else {
            return XCTFail("Expected the actual canvas input path to send a stroke")
        }
        XCTAssertEqual(point.x, 720, accuracy: 0.01)
        XCTAssertEqual(point.y, 405, accuracy: 0.01)
        let displayed = video.convert(CGPoint(x: 360, y: 202.5), to: screen)
        XCTAssertEqual(displayed.x, 240, accuracy: 0.01)
        XCTAssertEqual(displayed.y, 135, accuracy: 0.01)
        XCTAssertEqual(viewport.canvas.document.strokes.first?.points.first, point)
    }
}
