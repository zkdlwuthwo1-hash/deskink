import XCTest

final class DeskInkViewportUITests: XCTestCase {
    func testTwoFingerZoomPanAndFitKeepToolbarFixedWithoutDrawing() throws {
        continueAfterFailure = false
        XCUIDevice.shared.orientation = .portrait
        let app = XCUIApplication()
        app.launchArguments = ["-didSeeOnboarding", "YES", "-allowUSB", "NO"]
        app.launchEnvironment["EM_UI_PREVIEW"] = "deskink"
        app.launch()
        defer { app.terminate() }

        let viewport = app.descendants(matching: .any).matching(identifier: "deskink.viewport").firstMatch
        XCTAssertTrue(viewport.waitForExistence(timeout: 10))
        let marker = app.staticTexts["deskink.previewMarker"]
        XCTAssertTrue(marker.waitForExistence(timeout: 5))
        let initialMarker = marker.frame
        let eraser = app.buttons["지우개"]
        let toolbarButtonFrame = eraser.frame
        viewport.pinch(withScale: 2, velocity: 1)
        let zoomed = NSPredicate { _, _ in
            guard let value = viewport.value as? String, let percent = Int(value.replacingOccurrences(of: "%", with: "")) else { return false }
            return percent >= 150
        }
        expectation(for: zoomed, evaluatedWith: nil)
        waitForExpectations(timeout: 5)
        XCTAssertGreaterThan(marker.frame.width, initialMarker.width * 1.5)
        XCTAssertEqual(eraser.frame, toolbarButtonFrame)
        capture("deskink-pinched", app: app)

        let beforePan = marker.frame.midY
        let panned = expectation(description: "Two-finger pan")
        EMSwipeTwoFingers(CGPoint(x: viewport.frame.midX, y: viewport.frame.midY+100), 100) { success, error in
            XCTAssertTrue(success, error?.localizedDescription ?? "Touch synthesis failed")
            panned.fulfill()
        }
        wait(for: [panned], timeout: 10)
        expectation(for: NSPredicate { _, _ in marker.frame.midY < beforePan-20 }, evaluatedWith: nil)
        waitForExpectations(timeout: 5)
        XCTAssertEqual(app.staticTexts["deskink.previewEvents"].label, "필기 이벤트 0")
        XCTAssertEqual(eraser.frame, toolbarButtonFrame)
        eraser.tap()
        XCTAssertTrue(eraser.isSelected, "The toolbar must still receive taps while zoomed")
        capture("deskink-panned", app: app)

        app.buttons["deskink.fit"].tap()
        expectation(for: NSPredicate(format: "value == %@", "100%"), evaluatedWith: viewport)
        waitForExpectations(timeout: 5)
        XCTAssertEqual(marker.frame.midX, initialMarker.midX, accuracy: 1)
        XCTAssertEqual(marker.frame.midY, initialMarker.midY, accuracy: 1)
        XCTAssertEqual(marker.frame.width, initialMarker.width, accuracy: 1)
        capture("deskink-fit", app: app)
    }

    private func capture(_ name: String, app: XCUIApplication) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name; attachment.lifetime = .keepAlways
        add(attachment)
    }
}
