import SwiftUI

/// Reserve space for the controls so neither the fitted desktop nor zoomed ink
/// can slide underneath them. Only the canvas participates in zoom and pan.
struct DeskInkWorkspace<Canvas: View, Header: View>: View {
    let tools: DeskInkTools
    let toolsEnabled: Bool
    let canvas: Canvas
    let header: Header

    init(tools: DeskInkTools, toolsEnabled: Bool = true,
         @ViewBuilder canvas: () -> Canvas, @ViewBuilder header: () -> Header) {
        self.tools = tools
        self.toolsEnabled = toolsEnabled
        self.canvas = canvas()
        self.header = header()
    }

    var body: some View {
        VStack(spacing: 0) {
            header
                .padding(.horizontal, 16)
                .padding(.vertical, 4)
            canvas
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .clipped()
            DeskInkToolbar(tools: tools)
                .accessibilityIdentifier("deskink.toolbar")
                .disabled(!toolsEnabled)
                .padding(.horizontal, 16)
                .padding(.vertical, 4)
                .frame(maxWidth: .infinity)
        }
        // Leave room for a full-width 16:9 desktop on the 10.9-inch Air.
        // Controls keep their own margins; the video reaches the screen edges.
        .ignoresSafeArea(.container, edges: .horizontal)
        .background(Color.black.ignoresSafeArea())
        .statusBarHidden(true)
        .persistentSystemOverlays(.hidden)
    }
}
