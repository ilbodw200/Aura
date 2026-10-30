import SwiftUI
import Observation

@MainActor @Observable
final class AppStore {
    var snapshot = AppSnapshot()
    let tunnel = TunnelController()
    var message: String?
    var busy = false
    var events: [String] = []
    private var storageReady = false
    var selected: ProxyProfile? { snapshot.profiles.first { $0.id == snapshot.selectedID } }
    var favorites: [ProxyProfile] { snapshot.profiles.filter(\.favorite) }
    var accent: Color {
        switch snapshot.appearance.accent {
        case "blue": Color(red: 0.36, green: 0.60, blue: 1)
        case "violet": Color(red: 0.74, green: 0.56, blue: 1)
        case "rose": Color(red: 1, green: 0.47, blue: 0.65)
        case "amber": Color(red: 1, green: 0.75, blue: 0.36)
        default: Color(red: 0.43, green: 0.94, blue: 0.77)
        }
    }
    func load() async {
        do { snapshot = try SecureStore.load(); storageReady = true }
        catch { message = "Хранилище не открыто. Данные сохранены без изменений. \(error.localizedDescription)" }
        do { try await tunnel.load() } catch { log("Системный VPN-профиль пока недоступен.") }
    }
    func persist() {
        guard storageReady else { return }
        do { try SecureStore.save(snapshot) } catch { message = "Не удалось сохранить изменения: \(error.localizedDescription)" }
    }
    func log(_ event: String) {
        events.insert("\(Date.now.formatted(date: .omitted, time: .standard))  \(event)", at: 0)
        events = Array(events.prefix(100))
    }
    func requireDisconnected() throws {
        guard !tunnel.active else { throw AuraError.message("Сначала отключите VPN, чтобы изменить конфигурацию подключения.") }
    }
    func add(_ profiles: [ProxyProfile]) throws {
        try requireDisconnected()
        guard snapshot.profiles.count + profiles.count <= 1000 else { throw AuraError.message("Достигнут лимит 1000 профилей.") }
        snapshot.profiles.append(contentsOf: profiles)
        if snapshot.selectedID == nil { snapshot.selectedID = profiles.first?.id }
        persist(); log("Добавлено профилей: \(profiles.count).")
    }
    func select(_ id: UUID) {
        do { try requireDisconnected(); snapshot.selectedID = id; persist() } catch { message = error.localizedDescription }
    }
    func remove(_ id: UUID) {
        do {
            try requireDisconnected()
            snapshot.profiles.removeAll { $0.id == id }
            if snapshot.selectedID == id { snapshot.selectedID = snapshot.profiles.first?.id }
            persist()
        } catch { message = error.localizedDescription }
    }
    func favorite(_ id: UUID) {
        guard let index = snapshot.profiles.firstIndex(where: { $0.id == id }) else { return }
        snapshot.profiles[index].favorite.toggle(); persist()
    }
    func toggleConnection() async {
        guard !busy else { return }; busy = true; defer { busy = false }
        do {
            if tunnel.active { try await tunnel.disconnect(); log("Запрошено отключение.") }
            else {
                guard let selected else { throw AuraError.message("Сначала добавьте и выберите свой сервер во вкладке «Серверы».") }
                try await tunnel.connect(profile: selected, snapshot: snapshot)
                log("Запрошен запуск VPN. Статус определяется iOS.")
            }
        } catch { message = error.localizedDescription; log("Подключение не выполнено.") }
    }
    func refreshSubscription(_ id: UUID) async {
        guard !busy, let subscription = snapshot.subscriptions.first(where: { $0.id == id }) else { return }
        busy = true; defer { busy = false }
        do {
            try requireDisconnected()
            var incoming = try await SubscriptionService.fetch(subscription.url)
            // The user may have connected while the network request was running.
            try requireDisconnected()
            guard snapshot.subscriptions.contains(where: { $0.id == id }) else { return }
            let old = snapshot.profiles.filter { $0.subscriptionID == id }
            for index in incoming.indices {
                incoming[index].subscriptionID = id
                if let previous = old.first(where: { $0.kind == incoming[index].kind && $0.host == incoming[index].host && $0.port == incoming[index].port && $0.credential == incoming[index].credential }) {
                    incoming[index].id = previous.id; incoming[index].favorite = previous.favorite
                }
            }
            guard snapshot.profiles.count - old.count + incoming.count <= 1000 else { throw AuraError.message("В подписке слишком много серверов.") }
            snapshot.profiles.removeAll { $0.subscriptionID == id }; snapshot.profiles += incoming
            if !snapshot.profiles.contains(where: { $0.id == snapshot.selectedID }) { snapshot.selectedID = incoming.first?.id }
            if let index = snapshot.subscriptions.firstIndex(where: { $0.id == id }) { snapshot.subscriptions[index].updatedAt = .now; snapshot.subscriptions[index].lastError = nil }
            persist(); log("Подписка обновлена: \(incoming.count) серверов.")
        } catch {
            // URLs may contain provider tokens; never put raw network errors into the UI log.
            if let index = snapshot.subscriptions.firstIndex(where: { $0.id == id }) { snapshot.subscriptions[index].lastError = "Не удалось обновить. Предыдущие серверы сохранены." }
            persist(); message = "Подписка не обновлена. Проверьте HTTPS-ссылку и поддерживаемые форматы. Предыдущие серверы сохранены."
            log("Обновление подписки не выполнено.")
        }
    }
    func measure(_ profile: ProxyProfile) async {
        guard !profile.isExample else { message = "Примеры не содержат настоящего сервера."; return }
        do {
            let latency = try await LatencyService.measure(host: profile.host, port: profile.port)
            if let index = snapshot.profiles.firstIndex(where: { $0.id == profile.id }) {
                snapshot.profiles[index].latencyMS = latency; snapshot.profiles[index].latencyCheckedAt = .now; persist()
            }
        } catch { message = "Сервер не ответил на TCP-проверку. Для Hysteria2 это не проверка доступности UDP." }
    }
    func addExamples() {
        let examples = [ProxyProfile(name: "Amsterdam · пример", kind: .vless, host: "nl.example.invalid", port: 443, credential: UUID().uuidString, favorite: true, isExample: true), ProxyProfile(name: "Helsinki · пример", kind: .trojan, host: "fi.example.invalid", port: 443, credential: "example", isExample: true)]
        do { try add(examples) } catch { message = error.localizedDescription }
    }
}
