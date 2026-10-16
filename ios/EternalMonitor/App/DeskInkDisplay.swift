import SwiftUI

struct DeskInkDisplay: View {
    @EnvironmentObject private var connection: ConnectionManager

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Color.black.ignoresSafeArea()
            MetalView().ignoresSafeArea()
            DeskInkCanvas(videoSize: connection.videoSize,
                          enabled: !connection.signalLost && connection.transportMode == "USB",
                          connectionKey: "\(connection.hostInfo?.sessionId ?? 0):\(connection.hostStreamConfig?.streamEpoch ?? 0)",
                          send: connection.sendInk)
                .ignoresSafeArea()
            VStack(alignment: .trailing, spacing: 8) {
                Text(connection.signalLost ? "연결 복구 중" : "DeskInk · USB")
                Text("첫 연결 검증용 · 기본 펜")
                    .font(.caption)
            }
            .padding(12)
            .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 12))
            .padding()
            .allowsHitTesting(false)
        }
        .statusBarHidden(true)
        .persistentSystemOverlays(.hidden)
    }
}
