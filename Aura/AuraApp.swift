import SwiftUI

@main
struct AuraApp: App {
    @State private var store = AppStore()
    @Environment(\.scenePhase) private var phase
    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(store)
                .tint(store.accent)
                .preferredColorScheme(store.snapshot.appearance.colorScheme == "system" ? nil : store.snapshot.appearance.colorScheme == "light" ? .light : .dark)
                .task { await store.load() }
                .onChange(of: store.snapshot.appearance) { store.persist() }
                .onChange(of: phase) { if phase != .active { store.persist() } }
                .overlay {
                    if phase != .active && store.snapshot.hideInSwitcher {
                        ZStack { Color.black.ignoresSafeArea(); Label("AURA", systemImage: "circle.hexagongrid.fill").font(.largeTitle.bold()).foregroundStyle(.white) }
                    }
                }
        }
    }
}
