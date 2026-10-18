import Foundation

/// Keeps each monitor's ink and undo history across USB stream restarts.
/// A new host process has a new pairing token and therefore fresh PC boards.
@MainActor
final class DeskInkMonitorDocuments {
    private var hostToken: Data?
    private var documents: [String: DeskInkDocument] = [:]
    private(set) var current = DeskInkDocument()

    @discardableResult
    func select(hostToken: Data, monitor: String) -> DeskInkDocument {
        current.suspend()
        if self.hostToken != hostToken {
            documents.removeAll()
            self.hostToken = hostToken
        }
        let document = documents[monitor] ?? DeskInkDocument()
        documents[monitor] = document
        current = document
        return document
    }
}
