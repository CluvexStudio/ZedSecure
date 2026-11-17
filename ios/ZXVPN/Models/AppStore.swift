import Foundation
import SwiftUI

struct GhostSettings: Codable, Equatable {
    var antiPorn = false
    var extraDomains = ""
    var antiDPI = true
}

struct ServerEntry: Identifiable, Codable, Equatable {
    var id = UUID()
    var profile: AetherProfile
    var name: String { profile.profileName }
}

/// App state, persisted in UserDefaults.
@MainActor
final class AppStore: ObservableObject {
    @Published var servers: [ServerEntry] { didSet { save() } }
    @Published var activeID: UUID? { didSet { save() } }
    @Published var ghost: GhostSettings { didSet { save() } }

    private let defaults = UserDefaults.standard

    init() {
        if let data = defaults.data(forKey: "zx.servers"),
           let list = try? JSONDecoder().decode([ServerEntry].self, from: data) {
            servers = list
        } else {
            servers = []
        }
        if let raw = defaults.string(forKey: "zx.active") { activeID = UUID(uuidString: raw) } else { activeID = nil }
        if let data = defaults.data(forKey: "zx.ghost"),
           let g = try? JSONDecoder().decode(GhostSettings.self, from: data) {
            ghost = g
        } else {
            ghost = GhostSettings()
        }
    }

    var active: ServerEntry? { servers.first { $0.id == activeID } ?? servers.first }

    func add(_ profile: AetherProfile, activate: Bool = true) {
        let entry = ServerEntry(profile: profile)
        servers.append(entry)
        if activate { activeID = entry.id }
    }

    func remove(_ entry: ServerEntry) {
        servers.removeAll { $0.id == entry.id }
        if activeID == entry.id { activeID = servers.first?.id }
    }

    /// Block rules handed to the tunnel: Anti-Porn list + the user's own domains.
    var blockRules: [String] {
        guard ghost.antiPorn else { return [] }
        return AdultBlocklist.rules + AdultBlocklist.userRules(ghost.extraDomains)
    }

    private func save() {
        if let data = try? JSONEncoder().encode(servers) { defaults.set(data, forKey: "zx.servers") }
        defaults.set(activeID?.uuidString, forKey: "zx.active")
        if let data = try? JSONEncoder().encode(ghost) { defaults.set(data, forKey: "zx.ghost") }
    }
}
