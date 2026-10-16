import SwiftUI

struct ContentView: View {
    @EnvironmentObject var connectionManager: ConnectionManager

    var body: some View {
        switch connectionManager.state {
        case .disconnected, .connecting:
            ConnectView()
        case .connected:
            if (connectionManager.hostInfo?.hostCaps ?? 0) & 0x1000 != 0 {
                DeskInkDisplay()
            } else {
                DisplayView()
            }
        }
    }
}
