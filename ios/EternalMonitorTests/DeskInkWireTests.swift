import XCTest
@testable import EternalMonitor

final class DeskInkWireTests: XCTestCase {
    func testEncodedStrokeMatchesTheFixtureAcceptedByRust() throws {
        let commands: [DeskInkCommand] = [
            .begin(id: UInt64.max, brush: DeskInkBrush(),
                   point: DeskInkPoint(x: 100, y: 200, pressure: 0.5)),
            .append(id: UInt64.max, point: DeskInkPoint(x: 120, y: 215, pressure: 0.75)),
            .end(id: UInt64.max)
        ]
        let envelopes = commands.enumerated().map {
            DeskInkEnvelope(session: 42, epoch: 3, seq: UInt64($0.offset + 1), command: $0.element)
        }
        let bundle = Bundle(for: Self.self)
        let url = try XCTUnwrap(bundle.url(forResource: "deskink-ink", withExtension: "json", subdirectory: "testdata"))
        let expected = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? NSArray)
        let actual = try XCTUnwrap(JSONSerialization.jsonObject(with: JSONEncoder().encode(envelopes)) as? NSArray)
        XCTAssertEqual(actual, expected)
    }
}
