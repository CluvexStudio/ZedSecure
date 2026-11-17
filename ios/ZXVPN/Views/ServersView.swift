import SwiftUI

struct ServersView: View {
    @EnvironmentObject var store: AppStore
    @State private var showSmart = false
    @State private var showManual = false

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                HStack {
                    Text("Servers")
                        .font(.system(size: 26, weight: .black, design: .rounded))
                        .foregroundColor(.white)
                    Spacer()
                    Menu {
                        Button { showSmart = true } label: { Label("Smart config", systemImage: "bolt.fill") }
                        Button { showManual = true } label: { Label("Aether (manual)", systemImage: "slider.horizontal.3") }
                    } label: {
                        Image(systemName: "plus")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(.black)
                            .frame(width: 40, height: 40)
                            .background(ZX.green, in: Circle())
                    }
                }
                .padding(.top, 8)

                if store.servers.isEmpty {
                    ZXPanel(tint: ZX.cyan) {
                        VStack(alignment: .leading, spacing: 10) {
                            Text("Smart config builds a connectable Aether profile from your own network.")
                                .foregroundColor(.white)
                            Button { showSmart = true } label: {
                                Text("Smart config")
                                    .font(.system(size: 15, weight: .bold))
                                    .foregroundColor(.black)
                                    .padding(.horizontal, 18)
                                    .padding(.vertical, 10)
                                    .background(ZX.green, in: Capsule())
                            }
                        }
                    }
                }

                ForEach(store.servers) { server in
                    Button { store.activeID = server.id } label: {
                        ZXPanel(tint: store.active?.id == server.id ? ZX.green : .white) {
                            HStack {
                                VStack(alignment: .leading, spacing: 8) {
                                    Text(server.name).font(.headline).foregroundColor(.white)
                                    HStack(spacing: 6) {
                                        ZXChip(text: server.profile.transport.uppercased())
                                        ZXChip(text: server.profile.http2 ? "HTTP/2" : "HTTP/3")
                                        ZXChip(text: "scan \(server.profile.scanMode)", tint: ZX.green)
                                    }
                                }
                                Spacer()
                                if store.active?.id == server.id {
                                    Image(systemName: "checkmark.circle.fill").foregroundColor(ZX.green)
                                }
                            }
                        }
                    }
                    .buttonStyle(.plain)
                    .contextMenu {
                        Button(role: .destructive) { store.remove(server) } label: {
                            Label("Delete", systemImage: "trash")
                        }
                    }
                }
            }
            .padding(.horizontal, 20)
        }
        .sheet(isPresented: $showSmart) { SmartConfigView().environmentObject(store) }
        .sheet(isPresented: $showManual) { ManualAetherView().environmentObject(store) }
    }
}
