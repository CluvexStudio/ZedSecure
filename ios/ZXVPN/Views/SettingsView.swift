import SwiftUI

struct SettingsView: View {
    @EnvironmentObject var store: AppStore

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text("Settings")
                    .font(.system(size: 26, weight: .black, design: .rounded))
                    .foregroundColor(.white)
                    .padding(.top, 8)

                ZXSectionTitle(text: "GHOST MOD")
                ZXPanel(tint: ZX.green) {
                    VStack(alignment: .leading, spacing: 12) {
                        Toggle(isOn: $store.ghost.antiPorn) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Anti-Porn").foregroundColor(.white).font(.headline)
                                Text("Blocks adult sites and DNS-over-HTTPS bypass endpoints.")
                                    .font(.footnote).foregroundColor(ZX.dim)
                            }
                        }
                        .tint(ZX.green)
                        if store.ghost.antiPorn {
                            Text("Extra domains to block (one per line)")
                                .font(.footnote).foregroundColor(ZX.dim)
                            TextEditor(text: $store.ghost.extraDomains)
                                .frame(height: 90)
                                .scrollContentBackground(.hidden)
                                .padding(8)
                                .background(Color.white.opacity(0.06), in: RoundedRectangle(cornerRadius: 12))
                                .foregroundColor(.white)
                                .textInputAutocapitalization(.never)
                                .autocorrectionDisabled(true)
                            Text("Best-effort filter: direct IP access or another VPN can bypass it.")
                                .font(.caption).foregroundColor(ZX.dim)
                        }
                        Toggle(isOn: $store.ghost.antiDPI) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Anti-DPI").foregroundColor(.white).font(.headline)
                                Text("Use the strongest obfuscation the connection ladder allows.")
                                    .font(.footnote).foregroundColor(ZX.dim)
                            }
                        }
                        .tint(ZX.green)
                    }
                }

                ZXSectionTitle(text: "About")
                ZXPanel {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("ZX VPN for iOS").font(.headline).foregroundColor(.white)
                        Text("Native SwiftUI client. It shares the Aether profile format, Smart config rules and the Anti-Porn block list with the Android app.")
                            .font(.subheadline).foregroundColor(ZX.dim)
                        Text("The Aether engine for iOS is not bundled in this build yet, so the tunnel reports an error instead of connecting.")
                            .font(.footnote).foregroundColor(ZX.red)
                    }
                }
            }
            .padding(.horizontal, 20)
        }
    }
}
