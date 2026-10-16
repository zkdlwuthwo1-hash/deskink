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
                .padding(.vertical, 8)
            canvas
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .clipped()
            DeskInkToolbar(tools: tools)
                .accessibilityIdentifier("deskink.toolbar")
                .disabled(!toolsEnabled)
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .frame(maxWidth: .infinity)
        }
        .background(Color.black.ignoresSafeArea())
        .statusBarHidden(true)
        .persistentSystemOverlays(.hidden)
    }
}
