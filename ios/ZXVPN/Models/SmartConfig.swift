import Foundation
import Network

enum NetKind {
    case cellular, wifi, ethernet, unknown

    var label: String {
        switch self {
        case .cellular: return "Mobile data"
        case .wifi: return "Wi-Fi"
        case .ethernet: return "Ethernet"
        case .unknown: return "Unknown"
        }
    }

    var profileName: String {
        switch self {
        case .cellular: return "Aether · Smart · Mobile"
        case .wifi: return "Aether · Smart · Wi-Fi"
        case .ethernet: return "Aether · Smart · LAN"
        case .unknown: return "Aether · Smart"
        }
    }

    static func detect() async -> NetKind {
        await withCheckedContinuation { (cont: CheckedContinuation<NetKind, Never>) in
            let monitor = NWPathMonitor()
            let queue = DispatchQueue(label: "zx.path")
            let gate = OneShot()
            monitor.pathUpdateHandler = { path in
                guard gate.fire() else { return }
                monitor.cancel()
                if path.usesInterfaceType(.cellular) { cont.resume(returning: .cellular) }
                else if path.usesInterfaceType(.wifi) { cont.resume(returning: .wifi) }
                else if path.usesInterfaceType(.wiredEthernet) { cont.resume(returning: .ethernet) }
                else { cont.resume(returning: .unknown) }
            }
            monitor.start(queue: queue)
        }
    }
}

private final class OneShot {
    private let lock = NSLock()
    private var done = false
    func fire() -> Bool {
        lock.lock(); defer { lock.unlock() }
        if done { return false }
        done = true
        return true
    }
}

struct SmartInput {
    var network: NetKind = .unknown
    var hasIPv6 = false
    var countryCode: String?
    var edgeReachable: Bool?
    var edgeMs: Int?
    var apiReachable: Bool?
}

/// Builds an Aether profile from what the app learned about the user's network (same rules as Android).
enum SmartConfig {
    static let heavilyFiltered: Set<String> = ["IR", "CN", "RU", "TM", "BY", "MM"]

    static func build(_ input: SmartInput) -> AetherProfile {
        let cellular = input.network == .cellular
        let filtered = input.countryCode.map { heavilyFiltered.contains($0.uppercased()) } ?? false
        let edgeBlocked = input.edgeReachable == false
        let apiBlocked = input.apiReachable == false
        let fastEdge = input.edgeReachable == true && (input.edgeMs ?? Int.max) < 220
        let strict = filtered || edgeBlocked

        var p = AetherProfile()
        p.profileName = input.network.profileName
        p.transport = "masque"
        p.noize = strict ? "gfw" : (cellular ? "firewall" : "balanced")
        if edgeBlocked { p.scanMode = "thorough" }
        else if fastEdge && !filtered { p.scanMode = "turbo" }
        else if cellular || filtered { p.scanMode = "thorough" }
        else { p.scanMode = "balanced" }
        p.ipVersion = input.hasIPv6 ? "both" : "v4"
        p.http2 = cellular || strict
        p.fragment = p.http2 && strict
        p.apiAddress = apiBlocked ? "162.159.137.105" : ""
        return p
    }
}

/// Fast parallel reachability probes of the paths Aether depends on.
enum SmartProbe {
    static func time(_ urlString: String) async -> Int? {
        guard let url = URL(string: urlString) else { return nil }
        var request = URLRequest(url: url)
        request.timeoutInterval = 3.5
        request.cachePolicy = .reloadIgnoringLocalCacheData
        let start = Date()
        do {
            _ = try await URLSession.shared.data(for: request)
            return Int(Date().timeIntervalSince(start) * 1000)
        } catch {
            return nil
        }
    }

    struct Info: Decodable { let ip: String?; let country: String? }

    static func info() async -> Info? {
        guard let url = URL(string: "https://api.country.is") else { return nil }
        var request = URLRequest(url: url)
        request.timeoutInterval = 4
        guard let (data, _) = try? await URLSession.shared.data(for: request) else { return nil }
        return try? JSONDecoder().decode(Info.self, from: data)
    }
}
