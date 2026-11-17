import Foundation
import NetworkExtension

/// Owns the NETunnelProviderManager and exposes the tunnel status to SwiftUI.
@MainActor
final class VPNController: ObservableObject {
    @Published var status: NEVPNStatus = .disconnected
    @Published var errorText: String?
    @Published var connectedSince: Date?

    private var manager: NETunnelProviderManager?
    private var observer: NSObjectProtocol?  // lives for the whole app lifetime

    static let tunnelBundleID = "dev.zx.vpn.tunnel"

    init() {
        observer = NotificationCenter.default.addObserver(
            forName: .NEVPNStatusDidChange, object: nil, queue: .main
        ) { [weak self] _ in
            Task { @MainActor in self?.refreshStatus() }
        }
    }

    func load() async {
        do {
            let all = try await NETunnelProviderManager.loadAllFromPreferences()
            manager = all.first
            refreshStatus()
        } catch {
            errorText = error.localizedDescription
        }
    }

    private func refreshStatus() {
        let new = manager?.connection.status ?? .disconnected
        if new == .connected && status != .connected { connectedSince = Date() }
        if new != .connected { connectedSince = nil }
        status = new
    }

    func connect(profile: AetherProfile, blockRules: [String]) async {
        errorText = nil
        do {
            let m = manager ?? NETunnelProviderManager()
            let proto = NETunnelProviderProtocol()
            proto.providerBundleIdentifier = Self.tunnelBundleID
            proto.serverAddress = "ZX VPN"
            proto.providerConfiguration = [
                "profile": profile.encoded(),
                "blockRules": blockRules.joined(separator: "\n"),
            ]
            m.protocolConfiguration = proto
            m.localizedDescription = "ZX VPN"
            m.isEnabled = true
            try await m.saveToPreferences()
            try await m.loadFromPreferences()
            manager = m
            try m.connection.startVPNTunnel()
        } catch {
            errorText = error.localizedDescription
        }
        refreshStatus()
    }

    func disconnect() {
        manager?.connection.stopVPNTunnel()
    }
}
