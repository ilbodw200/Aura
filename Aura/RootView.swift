import SwiftUI

enum AppTab: Hashable { case home, profiles, rules, appearance, settings }
enum AppSheet: String, Identifiable { case importer, scanner, diagnostics, backup; var id: String { rawValue } }

struct RootView: View {
    @Environment(AppStore.self) private var store
    @State private var tab = AppTab.home
    @State private var sheet: AppSheet?
    var body: some View {
        TabView(selection: $tab) {
            NavigationStack { DashboardView(openSheet: { sheet = $0 }, showServers: { tab = .profiles }) }
                .tabItem { Label("Обзор", systemImage: "circle.hexagongrid") }.tag(AppTab.home)
            NavigationStack { ProfilesView(openSheet: { sheet = $0 }) }
                .tabItem { Label("Серверы", systemImage: "square.stack.3d.up") }.tag(AppTab.profiles)
            NavigationStack { RoutingView() }
                .tabItem { Label("Правила", systemImage: "arrow.triangle.branch") }.tag(AppTab.rules)
            NavigationStack { AppearanceView() }
                .tabItem { Label("Студия", systemImage: "slider.horizontal.3") }.tag(AppTab.appearance)
            NavigationStack { SettingsView(openSheet: { sheet = $0 }) }
                .tabItem { Label("Настройки", systemImage: "gearshape") }.tag(AppTab.settings)
        }
        .sheet(item: $sheet) { destination in
            switch destination {
            case .importer: ImportView()
            case .scanner: ScannerSheet()
            case .diagnostics: DiagnosticsView()
            case .backup: BackupView()
            }
        }
        .alert("AURA", isPresented: Binding(get: { store.message != nil }, set: { if !$0 { store.message = nil } })) { Button("Понятно") { store.message = nil } } message: { Text(store.message ?? "") }
        .onChange(of: store.tunnel.lastError) {
            if let error = store.tunnel.lastError { store.message = error; store.tunnel.lastError = nil }
        }
        .onReceive(NotificationCenter.default.publisher(for: .auraSubscriptionAdded)) { _ in tab = .profiles }
        .onOpenURL { url in
            guard url.scheme == "aura", url.host == "import", let components = URLComponents(url: url, resolvingAgainstBaseURL: false), let link = components.queryItems?.first(where: { $0.name == "url" })?.value else { store.message = "Неверная ссылка импорта AURA."; return }
            // Never fetch an external subscription without the user's review.
            NotificationCenter.default.post(name: .auraImportLink, object: link)
            PendingImport.link = link
            sheet = .importer
        }
    }
}

extension Notification.Name {
    static let auraImportLink = Notification.Name("auraImportLink")
    static let auraSubscriptionAdded = Notification.Name("auraSubscriptionAdded")
}
@MainActor enum PendingImport { static var link: String? }
