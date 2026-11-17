import Foundation

/// Same JSON shape as the Android `AetherProfile`, so profiles can be exchanged between platforms.
struct AetherProfile: Codable, Equatable {
    static let marker = "ZX_AETHER"

    var marker: String = AetherProfile.marker
    var profileName: String = "Aether"
    var transport: String = "masque"       // "protocol" in JSON
    var scanMode: String = "balanced"
    var ipVersion: String = "v4"
    var noize: String = "firewall"
    var http2: Bool = false
    var fragment: Bool = false
    var apiAddress: String = ""

    enum CodingKeys: String, CodingKey {
        case marker, profileName, scanMode, ipVersion, noize, http2, fragment, apiAddress
        case transport = "protocol"
    }

    static let protocols = ["masque", "wg", "gool", "mim"]
    static let scanModes = ["turbo", "balanced", "thorough", "verified", "ironclad"]
    static let ipVersions = ["v4", "v6", "both"]
    static let noizeProfiles = ["off", "light", "firewall", "balanced", "gfw", "aggressive"]

    init() {}

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        marker = try c.decodeIfPresent(String.self, forKey: .marker) ?? AetherProfile.marker
        profileName = try c.decodeIfPresent(String.self, forKey: .profileName) ?? "Aether"
        transport = try c.decodeIfPresent(String.self, forKey: .transport) ?? "masque"
        scanMode = try c.decodeIfPresent(String.self, forKey: .scanMode) ?? "balanced"
        ipVersion = try c.decodeIfPresent(String.self, forKey: .ipVersion) ?? "v4"
        noize = try c.decodeIfPresent(String.self, forKey: .noize) ?? "firewall"
        http2 = try c.decodeIfPresent(Bool.self, forKey: .http2) ?? false
        fragment = try c.decodeIfPresent(Bool.self, forKey: .fragment) ?? false
        apiAddress = try c.decodeIfPresent(String.self, forKey: .apiAddress) ?? ""
    }

    /// Command-line style arguments for the Aether core (identical to Android's `toArgs`).
    func args(bindPort: Int, workingDirectory: String) -> [String] {
        var a = ["--bind", "127.0.0.1:\(bindPort)", "--protocol", transport, "--scan", scanMode,
                 "--ip", ipVersion, "--noize", noize]
        if http2 { a.append("--h2") }
        if fragment && http2 { a.append("--fragment") }
        let api = apiAddress.trimmingCharacters(in: .whitespaces)
        if !api.isEmpty && api.allSatisfy({ $0.isLetter || $0.isNumber || ".:-[]".contains($0) }) {
            a += ["--api-address", api]
        }
        a += ["--config", workingDirectory + "/aether.toml"]
        return a
    }

    /// Ordered connection attempts: this profile, then more conservative variants.
    func attempts() -> [AetherProfile] {
        var list: [AetherProfile] = [self]
        func variant(_ change: (inout AetherProfile) -> Void) {
            var v = self
            change(&v)
            list.append(v)
        }
        if transport == "masque" { variant { $0.http2.toggle() } }
        variant { $0.transport = "masque"; $0.http2 = true; $0.fragment = true; $0.noize = "gfw"; $0.scanMode = "thorough" }
        variant {
            $0.transport = "masque"; $0.http2 = true; $0.fragment = true; $0.noize = "gfw"
            $0.scanMode = "thorough"; $0.apiAddress = "162.159.137.105"
        }
        variant { $0.transport = "wg"; $0.http2 = false; $0.noize = "balanced"; $0.scanMode = "balanced" }
        variant {
            $0.transport = "masque"; $0.http2 = false; $0.noize = "off"; $0.ipVersion = "v4"; $0.scanMode = "balanced"
        }
        var unique: [AetherProfile] = []
        for p in list where !unique.contains(p) { unique.append(p) }
        return unique
    }

    func encoded() -> String {
        guard let data = try? JSONEncoder().encode(self), let s = String(data: data, encoding: .utf8) else { return "{}" }
        return s
    }
}
