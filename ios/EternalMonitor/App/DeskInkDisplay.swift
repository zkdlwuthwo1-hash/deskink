import SwiftUI

struct DeskInkDisplay: View {
    @EnvironmentObject private var connection: ConnectionManager
    private var tools: DeskInkTools { connection.deskInkTools }

    private var toolsSupported: Bool { (connection.hostInfo?.hostCaps ?? 0) & 0x2000 != 0 }
    private var drawingSupported: Bool {
        toolsSupported && !connection.signalLost &&
            (connection.transportMode == "USB" || (connection.hostInfo?.hostCaps ?? 0) & HelloAck.hostCapReliableInk != 0)
    }

    var body: some View {
        DeskInkWorkspace(tools: tools, toolsEnabled: drawingSupported) {
            DeskInkViewport(frameSlot: connection.frameSlot, videoSize: connection.videoSize,
                          enabled: drawingSupported,
                          connectionKey: "\(connection.hostInfo?.sessionId ?? 0):\(connection.hostStreamConfig?.streamEpoch ?? 0)",
                          tools: tools,
                          send: connection.sendInk,
                          document: connection.deskInkDocuments.current)
        } header: {
            HStack {
                Label(connection.signalLost ? "연결 복구 중" : toolsSupported ? "DeskInk · \(connection.transportMode == "USB" ? "USB" : "Wi-Fi/LAN")" : "Windows DeskInk를 업데이트하세요", systemImage: connection.transportMode == "USB" ? "cable.connector" : "wifi")
                    .font(.caption.weight(.semibold)).padding(10).background(.regularMaterial, in: Capsule())
                Spacer()
                Button { tools.resetZoom() } label: {
                    Label("화면 맞춤", systemImage: "arrow.up.left.and.arrow.down.right")
                        .font(.caption.weight(.semibold)).padding(12).background(.regularMaterial, in: Capsule())
                }
                .accessibilityIdentifier("deskink.fit")
                Button { connection.cancel() } label: { Image(systemName: "xmark").frame(width:44,height:44).background(.regularMaterial,in:Circle()) }
                    .accessibilityLabel("연결 종료")
            }
        }
    }
}
