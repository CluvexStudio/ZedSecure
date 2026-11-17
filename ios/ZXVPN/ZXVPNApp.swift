import SwiftUI

@main
struct ZXVPNApp: App {
    @StateObject private var store = AppStore()
    @StateObject private var vpn = VPNController()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(store)
                .environmentObject(vpn)
                .preferredColorScheme(.dark)
                .task { await vpn.load() }
        }
    }
}
