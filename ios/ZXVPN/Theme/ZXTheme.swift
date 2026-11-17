import SwiftUI

/// ZX cyberpunk palette: neon green / red on near-black, matching the Android ZX theme.
enum ZX {
    static let bg = Color(red: 0.02, green: 0.03, blue: 0.05)
    static let green = Color(red: 0.10, green: 1.00, blue: 0.45)
    static let red = Color(red: 1.00, green: 0.12, blue: 0.20)
    static let cyan = Color(red: 0.10, green: 0.85, blue: 1.00)
    static let panel = Color.white.opacity(0.06)
    static let stroke = Color.white.opacity(0.10)
    static let dim = Color.white.opacity(0.60)
}

/// Dark backdrop with two soft neon glows whose colour follows the connection state.
struct ZXBackdrop: View {
    var accent: Color

    var body: some View {
        ZStack {
            ZX.bg
            RadialGradient(
                colors: [accent.opacity(0.32), .clear],
                center: .topLeading, startRadius: 10, endRadius: 460
            )
            RadialGradient(
                colors: [ZX.red.opacity(0.18), .clear],
                center: .bottomTrailing, startRadius: 10, endRadius: 420
            )
        }
        .ignoresSafeArea()
        .animation(.easeInOut(duration: 0.8), value: accent.description)
    }
}

/// Glass panel used by every card in the app.
struct ZXPanel<Content: View>: View {
    var tint: Color = .white
    @ViewBuilder var content: () -> Content

    var body: some View {
        content()
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(.ultraThinMaterial.opacity(0.55), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            .background(tint.opacity(0.05), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .stroke(ZX.stroke, lineWidth: 1)
            )
    }
}

struct ZXChip: View {
    let text: String
    var tint: Color = ZX.cyan

    var body: some View {
        Text(text)
            .font(.system(size: 11, weight: .semibold, design: .monospaced))
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(tint.opacity(0.14), in: Capsule())
            .overlay(Capsule().stroke(tint.opacity(0.4), lineWidth: 1))
            .foregroundColor(tint)
    }
}

struct ZXSectionTitle: View {
    let text: LocalizedStringKey
    var body: some View {
        Text(text)
            .font(.system(size: 12, weight: .bold, design: .monospaced))
            .foregroundColor(ZX.dim)
            .textCase(.uppercase)
            .padding(.top, 6)
    }
}
