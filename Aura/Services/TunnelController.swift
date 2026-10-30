import Foundation
import Observation
import NetworkExtension

@MainActor @Observable
final class TunnelController {
    private(set) var status = NEVPNStatus.disconnected
    private(set) var connectedAt: Date?
    var lastError: String?
    private var manager: NETunnelProviderManager?
    private var tunnelBundleID: String {
        // Signing tools can rename the extension. Read its actual embedded ID.
        if let directory = Bundle.main.builtInPlugInsURL,
           let extensions = try? FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil),
           let url = extensions.first(where: { $0.lastPathComponent == "AuraTunnel.appex" }),
           let identifier = Bundle(url: url)?.bundleIdentifier { return identifier }
        return Bundle.main.object(forInfoDictionaryKey: "AuraTunnelBundleIdentifier") as? String ?? "app.aura.proxy.tunnel"
    }
    @ObservationIgnored private var observation: VPNStatusObservation?
    var active: Bool { status == .connected || status == .connecting || status == .reasserting }
    var title: String {
        switch status {
        case .connected: "Подключено"
        case .connecting: "Подключение…"
        case .disconnecting: "Отключение…"
        case .reasserting: "Восстановление…"
        default: "Не подключено"
        }
    }
    init() {
        observation = VPNStatusObservation { [weak self] in
            Task { @MainActor [weak self] in self?.refreshStatus() }
        }
    }
    func load() async throws {
        let managers = try await NETunnelProviderManager.loadAllFromPreferences()
        manager = managers.first { ($0.protocolConfiguration as? NETunnelProviderProtocol)?.providerBundleIdentifier == tunnelBundleID }
        refreshStatus()
    }
    private func refreshStatus() {
        let previous = status
        status = manager?.connection.status ?? .disconnected
        connectedAt = manager?.connection.connectedDate
        if status == .disconnected && (previous == .connecting || previous == .reasserting || previous == .connected) {
            manager?.connection.fetchLastDisconnectError { [weak self] error in
                guard error != nil else { return }
                Task { @MainActor [weak self] in self?.lastError = "iOS остановила VPN. Проверьте сборку Libbox, подпись расширения и параметры сервера. Подробности доступны в системном журнале Network Extension." }
            }
        }
    }
    func connect(profile: ProxyProfile, snapshot: AppSnapshot) async throws {
        let data = try ConfigBuilder.build(profile: profile, snapshot: snapshot)
        let configuration = String(decoding: data, as: UTF8.self)
        let tunnel = manager ?? NETunnelProviderManager()
        let config = NETunnelProviderProtocol()
        config.providerBundleIdentifier = tunnelBundleID
        config.serverAddress = profile.host
        config.disconnectOnSleep = false
        config.providerConfiguration = [TunnelPayload.key: configuration]
        tunnel.protocolConfiguration = config
        tunnel.localizedDescription = "AURA"
        tunnel.isEnabled = true
        tunnel.isOnDemandEnabled = snapshot.onDemand
        if snapshot.onDemand {
            let rule = NEOnDemandRuleConnect(); rule.interfaceTypeMatch = .any
            tunnel.onDemandRules = [rule]
        } else { tunnel.onDemandRules = [] }
        try await tunnel.saveToPreferences()
        try await tunnel.loadFromPreferences()
        manager = tunnel
        try tunnel.connection.startVPNTunnel(options: [TunnelPayload.key: configuration as NSString])
        refreshStatus()
    }
    func disconnect() async throws {
        // Disable on-demand first, otherwise iOS can immediately reconnect.
        if let manager, manager.isOnDemandEnabled {
            manager.isOnDemandEnabled = false
            try await manager.saveToPreferences()
        }
        manager?.connection.stopVPNTunnel()
        refreshStatus()
    }
}

/// Owns the NotificationCenter token outside actor isolation, so teardown can
/// safely remove it even when Swift releases the controller off the main actor.
private final class VPNStatusObservation {
    private let token: NSObjectProtocol
    init(onChange: @escaping () -> Void) {
        token = NotificationCenter.default.addObserver(forName: .NEVPNStatusDidChange, object: nil, queue: .main) { _ in onChange() }
    }
    deinit { NotificationCenter.default.removeObserver(token) }
}
