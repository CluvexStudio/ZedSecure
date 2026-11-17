import SwiftUI
import NetworkExtension

struct HomeView: View {
    @EnvironmentObject var store: AppStore
    @EnvironmentObject var vpn: VPNController

    private var busy: Bool { vpn.status == .connecting || vpn.status == .reasserting || vpn.status == .disconnecting }

    private var statusText: LocalizedStringKey {
        switch vpn.status {
        case .connected: return "Protected"
        case .connecting, .reasserting: return "Connecting…"
        case .disconnecting: return "Disconnecting…"
        default: return "Not connected"
        }
    }

    private var tint: Color {
        switch vpn.status {
        case .connected: return ZX.green
        case .connecting, .reasserting: return ZX.cyan
        default: return ZX.red
        }
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 22) {
                HStack {
                    Text("ZX VPN")
                        .font(.system(size: 26, weight: .black, design: .rounded))
                        .foregroundColor(.white)
                    Spacer()
                    ZXChip(text: "AETHER", tint: tint)
                }
                .padding(.top, 8)

                ConnectOrb(status: vpn.status, tint: tint) { toggle() }
                    .frame(height: 280)

                VStack(spacing: 6) {
                    Text(statusText)
                        .font(.system(size: 22, weight: .bold, design: .rounded))
                        .foregroundColor(tint)
                    if let since = vpn.connectedSince {
                        TimelineView(.periodic(from: since, by: 1)) { ctx in
                            Text(format(ctx.date.timeIntervalSince(since)))
                                .font(.system(size: 15, design: .monospaced))
                                .foregroundColor(ZX.dim)
                        }
                    }
                    if let error = vpn.errorText {
                        Text(error)
                            .font(.footnote)
                            .multilineTextAlignment(.center)
                            .foregroundColor(ZX.red)
                            .padding(.horizontal, 24)
                    }
                }

                ZXPanel(tint: tint) {
                    VStack(alignment: .leading, spacing: 8) {
                        ZXSectionTitle(text: "Active server")
                        if let server = store.active {
                            Text(server.name).font(.headline).foregroundColor(.white)
                            HStack(spacing: 6) {
                                ZXChip(text: server.profile.transport.uppercased())
                                ZXChip(text: server.profile.http2 ? "HTTP/2" : "HTTP/3")
                                ZXChip(text: "noize \(server.profile.noize)", tint: ZX.green)
                            }
                        } else {
                            Text("No server yet. Open Servers and create a Smart config.")
                                .font(.subheadline)
                                .foregroundColor(ZX.dim)
                        }
                    }
                }

                ZXPanel(tint: ZX.green) {
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            ZXSectionTitle(text: "GHOST MOD")
                            Text(store.ghost.antiPorn ? "Anti-Porn is on" : "Anti-Porn is off")
                                .foregroundColor(.white)
                        }
                        Spacer()
                        Image(systemName: store.ghost.antiPorn ? "shield.checkered" : "shield")
                            .font(.system(size: 26))
                            .foregroundColor(store.ghost.antiPorn ? ZX.green : ZX.dim)
                    }
                }
            }
            .padding(.horizontal, 20)
        }
    }

    private func toggle() {
        if vpn.status == .connected || busy {
            vpn.disconnect()
        } else if let server = store.active {
            let rules = store.blockRules
            Task { await vpn.connect(profile: server.profile, blockRules: rules) }
        } else {
            vpn.errorText = "Create a server first (Servers → Smart config)."
        }
    }

    private func format(_ t: TimeInterval) -> String {
        let s = Int(t)
        return String(format: "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    }
}

/// The big connect button: rotating neon rings, pulse while connecting.
struct ConnectOrb: View {
    let status: NEVPNStatus
    let tint: Color
    let action: () -> Void

    var body: some View {
        TimelineView(.animation) { timeline in
            let t = timeline.date.timeIntervalSinceReferenceDate
            let busy = status == .connecting || status == .reasserting
            let pulse = busy ? 1 + 0.04 * sin(t * 6) : 1.0
            ZStack {
                Circle()
                    .fill(tint.opacity(0.10))
                    .frame(width: 250, height: 250)
                    .blur(radius: 18)
                Circle()
                    .trim(from: 0, to: 0.72)
                    .stroke(
                        AngularGradient(colors: [tint, tint.opacity(0.05), tint], center: .center),
                        style: StrokeStyle(lineWidth: 3, lineCap: .round)
                    )
                    .frame(width: 236, height: 236)
                    .rotationEffect(.degrees(t * (busy ? 160 : 40)))
                Circle()
                    .trim(from: 0, to: 0.5)
                    .stroke(ZX.cyan.opacity(0.5), style: StrokeStyle(lineWidth: 2, lineCap: .round, dash: [6, 8]))
                    .frame(width: 206, height: 206)
                    .rotationEffect(.degrees(-t * 55))
                Button(action: action) {
                    ZStack {
                        Circle()
                            .fill(
                                RadialGradient(
                                    colors: [tint.opacity(0.55), ZX.bg],
                                    center: .center, startRadius: 4, endRadius: 80
                                )
                            )
                        Circle().stroke(tint, lineWidth: 2)
                        Image(systemName: "power")
                            .font(.system(size: 54, weight: .bold))
                            .foregroundColor(.white)
                            .shadow(color: tint, radius: 12)
                    }
                    .frame(width: 160, height: 160)
                    .scaleEffect(pulse)
                }
                .buttonStyle(.plain)
            }
            .frame(maxWidth: .infinity)
        }
    }
}
