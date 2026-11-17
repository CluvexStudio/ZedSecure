import SwiftUI

enum StepState {
    case pending
    case ok(String)
    case fail(String)
}

@MainActor
final class SmartModel: ObservableObject {
    @Published var net: StepState = .pending
    @Published var edge: StepState = .pending
    @Published var api: StepState = .pending
    @Published var ip: StepState = .pending
    @Published var profile: AetherProfile?
    @Published var blocked = false

    func run() async {
        let kind = await NetKind.detect()
        net = .ok(kind.label)

        async let edgeTime = SmartProbe.time("https://cp.cloudflare.com/generate_204")
        async let apiTime = SmartProbe.time("https://api.devices.cloudflare.com/")
        async let info = SmartProbe.info()

        let e = await edgeTime
        edge = e.map { .ok("\($0) ms") } ?? .fail("blocked")

        var a = await apiTime
        if a == nil { a = await SmartProbe.time("https://api.cloudflareclient.com/") }
        api = a != nil ? .ok("reachable") : .fail("blocked")

        let i = await info
        let v6 = i?.ip?.contains(":") ?? false
        ip = v6 ? .ok("IPv6") : .fail("n/a")

        var input = SmartInput()
        input.network = kind
        input.hasIPv6 = v6
        input.countryCode = i?.country
        input.edgeReachable = e != nil
        input.edgeMs = e
        input.apiReachable = a != nil
        blocked = e == nil || a == nil
        profile = SmartConfig.build(input)
    }
}

struct SmartConfigView: View {
    @EnvironmentObject var store: AppStore
    @Environment(\.dismiss) private var dismiss
    @StateObject private var model = SmartModel()
    @State private var showManual = false

    var body: some View {
        ZStack {
            ZXBackdrop(accent: ZX.cyan)
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text("Smart config")
                        .font(.system(size: 26, weight: .black, design: .rounded))
                        .foregroundColor(.white)
                        .padding(.top, 24)
                    Text("Reads your network and fills every Aether field for you.")
                        .font(.subheadline)
                        .foregroundColor(ZX.dim)

                    if model.profile == nil {
                        ProgressView().tint(ZX.cyan)
                    }

                    ZXPanel(tint: ZX.cyan) {
                        VStack(spacing: 12) {
                            StepRow(title: "Network", state: model.net)
                            StepRow(title: "Cloudflare edge", state: model.edge)
                            StepRow(title: "WARP API", state: model.api)
                            StepRow(title: "IPv6", state: model.ip)
                        }
                    }

                    if let p = model.profile {
                        ZXPanel(tint: ZX.green) {
                            VStack(alignment: .leading, spacing: 8) {
                                Text(p.profileName).font(.headline).foregroundColor(.white)
                                HStack(spacing: 6) {
                                    ZXChip(text: p.transport.uppercased())
                                    ZXChip(text: p.http2 ? "HTTP/2" : "HTTP/3")
                                    ZXChip(text: p.ipVersion)
                                }
                                HStack(spacing: 6) {
                                    ZXChip(text: "scan \(p.scanMode)", tint: ZX.green)
                                    ZXChip(text: "noize \(p.noize)", tint: ZX.green)
                                    if p.fragment { ZXChip(text: "fragment", tint: ZX.red) }
                                }
                            }
                        }
                        if model.blocked {
                            Text("Some paths are blocked on your network. The strongest anti-filter settings were chosen and the connection retries other methods automatically.")
                                .font(.footnote)
                                .foregroundColor(ZX.cyan)
                        }
                        Button {
                            store.add(p)
                            dismiss()
                        } label: {
                            Text("Create and activate")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(.black)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 14)
                                .background(ZX.green, in: Capsule())
                        }
                    }
                    Button("Cancel") { dismiss() }
                        .foregroundColor(ZX.dim)
                        .frame(maxWidth: .infinity)
                }
                .padding(.horizontal, 20)
            }
        }
        .task { await model.run() }
    }
}

struct StepRow: View {
    let title: LocalizedStringKey
    let state: StepState

    var body: some View {
        HStack(spacing: 10) {
            switch state {
            case .pending:
                ProgressView().scaleEffect(0.7).frame(width: 20, height: 20)
            case .ok:
                Image(systemName: "checkmark.circle.fill").foregroundColor(ZX.green).frame(width: 20, height: 20)
            case .fail:
                Image(systemName: "xmark.circle.fill").foregroundColor(ZX.red).frame(width: 20, height: 20)
            }
            Text(title).foregroundColor(.white)
            Spacer()
            switch state {
            case .pending: EmptyView()
            case .ok(let detail): Text(detail).font(.system(size: 12, design: .monospaced)).foregroundColor(ZX.dim)
            case .fail(let detail): Text(detail).font(.system(size: 12, design: .monospaced)).foregroundColor(ZX.dim)
            }
        }
    }
}
