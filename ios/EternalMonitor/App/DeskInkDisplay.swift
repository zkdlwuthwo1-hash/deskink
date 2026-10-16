import SwiftUI

struct DeskInkDisplay: View {
    @EnvironmentObject private var connection: ConnectionManager
    @State private var tools = DeskInkTools()

    private var toolsSupported: Bool { (connection.hostInfo?.hostCaps ?? 0) & 0x2000 != 0 }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            DeskInkViewport(frameSlot: connection.frameSlot, videoSize: connection.videoSize,
                          enabled: !connection.signalLost && connection.transportMode == "USB" && toolsSupported,
                          connectionKey: "\(connection.hostInfo?.sessionId ?? 0):\(connection.hostStreamConfig?.streamEpoch ?? 0)",
                          tools: tools,
                          send: connection.sendInk)
                .ignoresSafeArea()
            VStack {
                HStack {
                    Label(connection.signalLost ? "연결 복구 중" : toolsSupported ? "DeskInk · USB" : "Windows DeskInk를 업데이트하세요", systemImage: "cable.connector")
                        .font(.caption.weight(.semibold)).padding(10).background(.regularMaterial, in: Capsule())
                    Spacer()
                    Button { tools.resetZoom() } label: {
                        Label("화면 맞춤", systemImage: "arrow.up.left.and.arrow.down.right")
                            .font(.caption.weight(.semibold)).padding(12).background(.regularMaterial, in: Capsule())
                    }
                    .accessibilityIdentifier("deskink.fit")
                    Button { connection.disconnect() } label: { Image(systemName: "xmark").frame(width:44,height:44).background(.regularMaterial,in:Circle()) }
                        .accessibilityLabel("연결 종료")
                }
                Spacer()
                DeskInkToolbar(tools: tools).disabled(!toolsSupported || connection.signalLost)
            }
            .padding(16)
        }
        .statusBarHidden(true)
        .persistentSystemOverlays(.hidden)
    }
}
