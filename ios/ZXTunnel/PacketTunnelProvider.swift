import NetworkExtension

/// Packet tunnel extension.
///
/// The Aether engine for iOS (the Rust core builds `libaether.a` with a C API) is not bundled in
/// this build, so starting the tunnel reports a clear error instead of pretending to connect.
/// Integration point: create the engine from `providerConfiguration["profile"]`, apply the
/// `blockRules`, configure `NEPacketTunnelNetworkSettings`, then pump packets between
/// `packetFlow` and the engine's local SOCKS listener.
final class PacketTunnelProvider: NEPacketTunnelProvider {
    override func startTunnel(
        options: [String: NSObject]?,
        completionHandler: @escaping (Error?) -> Void
    ) {
        let error = NSError(
            domain: "ZXTunnel",
            code: 1,
            userInfo: [NSLocalizedDescriptionKey: "The Aether engine is not bundled in this iOS build yet."]
        )
        completionHandler(error)
    }

    override func stopTunnel(
        with reason: NEProviderStopReason,
        completionHandler: @escaping () -> Void
    ) {
        completionHandler()
    }
}
