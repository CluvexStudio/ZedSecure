import SwiftUI
import NetworkExtension

struct RootView: View {
    @EnvironmentObject var vpn: VPNController
    @State private var tab = 0

    private var accent: Color {
        switch vpn.status {
        case .connected: return ZX.green
        case .connecting, .reasserting: return ZX.cyan
        default: return ZX.red
        }
    }

    var body: some View {
        ZStack(alignment: .bottom) {
            ZXBackdrop(accent: accent)
            Group {
                switch tab {
                case 0: HomeView()
                case 1: ServersView()
                default: SettingsView()
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .padding(.bottom, 84)
            ZXTabBar(selection: $tab, accent: accent)
        }
    }
}

/// Floating glass tab bar.
struct ZXTabBar: View {
    @Binding var selection: Int
    var accent: Color

    private let items: [(String, LocalizedStringKey)] = [
        ("bolt.shield.fill", "Home"),
        ("server.rack", "Servers"),
        ("slider.horizontal.3", "Settings"),
    ]

    var body: some View {
        HStack(spacing: 6) {
            ForEach(0..<items.count, id: \.self) { i in
                Button {
                    withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) { selection = i }
                } label: {
                    VStack(spacing: 3) {
                        Image(systemName: items[i].0).font(.system(size: 18, weight: .semibold))
                        Text(items[i].1).font(.system(size: 10, weight: .semibold))
                    }
                    .foregroundColor(selection == i ? accent : ZX.dim)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                    .background(
                        Capsule().fill(selection == i ? accent.opacity(0.16) : Color.clear)
                    )
                }
            }
        }
        .padding(6)
        .background(.ultraThinMaterial, in: Capsule())
        .overlay(Capsule().stroke(ZX.stroke, lineWidth: 1))
        .padding(.horizontal, 24)
        .padding(.bottom, 8)
    }
}
