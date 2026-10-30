import SwiftUI
import Observation

@MainActor @Observable
final class AppStore {
    var snapshot = AppSnapshot()
    let tunnel = TunnelController()
    var message: String?
    var busy = false
    var events: [String] = []
    var probing: Set<UUID> = []
    var pingErrors: [UUID: String] = [:]
    var batchProbing = false
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
    func addSubscription(name: String, url: URL, sendHWID: Bool, allowHTTP: Bool, verifiedURL: URL?, contents: SubscriptionContents?) throws -> UUID {
        try requireDisconnected()
        guard storageReady else { throw AuraError.message("Хранилище недоступно. Ссылка не была сохранена.") }
        guard !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { throw AuraError.message("Введите название подписки.") }
        try SubscriptionRequest.validateForSaving(url: url, allowHTTP: allowHTTP, verifiedURL: verifiedURL, profileCount: contents?.profiles.count ?? 0)
        guard !snapshot.subscriptions.contains(where: { $0.url == url }) else { throw AuraError.message("Эта подписка уже добавлена. Обновите её во вкладке «Серверы».") }
        guard snapshot.profiles.count + (contents?.profiles.count ?? 0) <= 1000 else { throw AuraError.message("Достигнут лимит 1000 профилей.") }
        let subscription = Subscription(name: name, url: url, updatedAt: contents == nil ? nil : .now, sendHWID: url.scheme?.lowercased() == "https" && sendHWID, allowHTTP: allowHTTP, lastWarning: contents?.warnings.isEmpty == false ? contents?.warnings.prefix(5).joined(separator: "\n") : nil)
        var savedSubscription = subscription; savedSubscription.usage = contents?.usage
        snapshot.subscriptions.append(savedSubscription)
        if let contents {
            var profiles = contents.profiles
            for index in profiles.indices { profiles[index].subscriptionID = subscription.id }
            snapshot.profiles += profiles
            if snapshot.selectedID == nil { snapshot.selectedID = profiles.first?.id }
        }
        persist()
        return subscription.id
    }
    func refreshSubscription(_ id: UUID, reportError: Bool = true) async {
        guard !busy, let subscription = snapshot.subscriptions.first(where: { $0.id == id }) else { return }
        busy = true; defer { busy = false }
        do {
            try requireDisconnected()
            let metadata = subscription.url.scheme?.lowercased() == "https" && (subscription.sendHWID ?? snapshot.sendHWID ?? false) ? try DeviceIdentity.metadata() : nil
            let result = try await SubscriptionService.fetch(subscription.url, metadata: metadata, allowHTTP: subscription.allowHTTP ?? false)
            var incoming = result.profiles
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
            // A partial response must not delete previously imported servers that
            // could not be decoded in this response.
            let retained = result.skippedCount == 0 ? [] : old.filter { previous in !incoming.contains(where: { $0.id == previous.id }) }
            guard snapshot.profiles.count - old.count + incoming.count + retained.count <= 1000 else { throw AuraError.message("Достигнут лимит 1000 профилей.") }
            snapshot.profiles.removeAll { $0.subscriptionID == id }; snapshot.profiles += incoming + retained
            if !snapshot.profiles.contains(where: { $0.id == snapshot.selectedID }) { snapshot.selectedID = incoming.first?.id }
            if let index = snapshot.subscriptions.firstIndex(where: { $0.id == id }) { snapshot.subscriptions[index].usage = result.usage; snapshot.subscriptions[index].updatedAt = .now; snapshot.subscriptions[index].lastError = nil; snapshot.subscriptions[index].lastWarning = result.warnings.isEmpty ? nil : result.warnings.prefix(5).joined(separator: "\n") }
            persist(); log("Подписка обновлена: \(incoming.count) серверов.")
        } catch {
            // URLs may contain provider tokens; never put raw network errors into the UI log.
            let detail = SubscriptionService.message(for: error)
            if let index = snapshot.subscriptions.firstIndex(where: { $0.id == id }) { snapshot.subscriptions[index].lastError = detail }
            persist(); if reportError { message = detail }
            log("Обновление подписки не выполнено.")
        }
    }
    func measure(_ profile: ProxyProfile, reportError: Bool = true) async {
        guard !probing.contains(profile.id) else { return }
        guard !profile.isExample else { if reportError { message = "Примеры не содержат настоящего сервера." }; return }
        probing.insert(profile.id); pingErrors[profile.id] = nil
        let settings = snapshot.ping ?? PingSettings()
        defer { probing.remove(profile.id) }
        do {
            let result = try await ProbeService.measure(profile: profile, snapshot: snapshot, settings: settings)
            if let index = snapshot.profiles.firstIndex(where: { $0.id == profile.id }) {
                snapshot.profiles[index].latencyMS = result.ms
                snapshot.profiles[index].latencyCheckedAt = .now
                snapshot.profiles[index].pingMethod = settings.method.rawValue
                snapshot.profiles[index].pingStatus = result.status
                persist()
            }
        } catch {
            pingErrors[profile.id] = "Нет ответа · \(settings.method.rawValue)"
            if let index = snapshot.profiles.firstIndex(where: { $0.id == profile.id }) { snapshot.profiles[index].latencyMS = nil; snapshot.profiles[index].pingStatus = nil; persist() }
            if reportError { message = error.localizedDescription }
        }
    }
    func measureGroup(_ profiles: [ProxyProfile]) async {
        guard !batchProbing else { return }; batchProbing = true
        defer { batchProbing = false }
        // Bound traffic and core memory: two isolated probes at a time.
        for offset in stride(from: 0, to: profiles.count, by: 2) {
            await withTaskGroup(of: Void.self) { group in
                for profile in profiles[offset..<min(offset + 2, profiles.count)] { group.addTask { await self.measure(profile, reportError: false) } }
            }
        }
    }
    func addExamples() {
        let examples = [ProxyProfile(name: "Amsterdam · пример", kind: .vless, host: "nl.example.invalid", port: 443, credential: UUID().uuidString, favorite: true, isExample: true), ProxyProfile(name: "Helsinki · пример", kind: .trojan, host: "fi.example.invalid", port: 443, credential: "example", isExample: true)]
        do { try add(examples) } catch { message = error.localizedDescription }
    }
}
