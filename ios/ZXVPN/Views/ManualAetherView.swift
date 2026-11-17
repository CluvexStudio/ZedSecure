import SwiftUI

struct ManualAetherView: View {
    @EnvironmentObject var store: AppStore
    @Environment(\.dismiss) private var dismiss
    @State private var profile = AetherProfile()

    var body: some View {
        NavigationView {
            Form {
                Section {
                    TextField("Name", text: $profile.profileName)
                    Picker("Protocol", selection: $profile.transport) {
                        ForEach(AetherProfile.protocols, id: \.self) { Text($0.uppercased()).tag($0) }
                    }
                    Picker("Scan", selection: $profile.scanMode) {
                        ForEach(AetherProfile.scanModes, id: \.self) { Text($0).tag($0) }
                    }
                    Picker("IP version", selection: $profile.ipVersion) {
                        ForEach(AetherProfile.ipVersions, id: \.self) { Text($0).tag($0) }
                    }
                    Picker("Anti-DPI (noize)", selection: $profile.noize) {
                        ForEach(AetherProfile.noizeProfiles, id: \.self) { Text($0).tag($0) }
                    }
                    Toggle("HTTP/2", isOn: $profile.http2)
                    Toggle("Fragment ClientHello", isOn: $profile.fragment)
                    TextField("API address (optional)", text: $profile.apiAddress)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled(true)
                }
            }
            .navigationTitle("Aether (manual)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        if profile.transport == "wg" { profile.http2 = false }
                        store.add(profile)
                        dismiss()
                    }
                }
            }
        }
    }
}
