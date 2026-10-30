#if canImport(Libbox)
import Foundation
import Libbox
import Network
import NetworkExtension

/// Platform bridge for the pinned sing-box v1.11.15 Go-mobile API.
/// Re-check the protocol against generated Libbox headers when updating the core.
final class BoxPlatform: NSObject, LibboxPlatformInterfaceProtocol {
    private unowned let provider: PacketTunnelProvider
    private var monitor: NWPathMonitor?
    private let monitorQueue = DispatchQueue(label: "app.aura.tunnel.path")
    init(provider: PacketTunnelProvider) { self.provider = provider }

    func openTun(_ options: LibboxTunOptionsProtocol?, ret0_: UnsafeMutablePointer<Int32>?) throws {
        guard let options, let ret0_ else { throw AuraError.message("Ядро не передало настройки TUN.") }
        let settings = NEPacketTunnelNetworkSettings(tunnelRemoteAddress: "127.0.0.1")
        settings.mtu = NSNumber(value: options.getMTU())
        var v4: [String] = [], masks: [String] = [], v6: [String] = [], prefixes: [NSNumber] = []
        if let addresses = options.getInet4Address() { while addresses.hasNext() { if let prefix = addresses.next() { v4.append(prefix.address()); masks.append(prefix.mask()) } } }
        if let addresses = options.getInet6Address() { while addresses.hasNext() { if let prefix = addresses.next() { v6.append(prefix.address()); prefixes.append(NSNumber(value: prefix.prefix())) } } }
        if !v4.isEmpty {
            let ipv4 = NEIPv4Settings(addresses: v4, subnetMasks: masks)
            ipv4.includedRoutes = [NEIPv4Route.default()]
            settings.ipv4Settings = ipv4
        }
        if !v6.isEmpty {
            let ipv6 = NEIPv6Settings(addresses: v6, networkPrefixLengths: prefixes)
            ipv6.includedRoutes = [NEIPv6Route.default()]
            settings.ipv6Settings = ipv6
        }
        let dns = try options.getDNSServerAddress()
        settings.dnsSettings = NEDNSSettings(servers: [dns.value])
        settings.dnsSettings?.matchDomains = [""]
        settings.dnsSettings?.matchDomainsNoSearch = true
        let semaphore = DispatchSemaphore(value: 0)
        var failure: Error?
        provider.setTunnelNetworkSettings(settings) { error in failure = error; semaphore.signal() }
        guard semaphore.wait(timeout: .now() + 10) == .success else { throw AuraError.message("iOS не применила настройки туннеля за 10 секунд.") }
        if let failure { throw failure }
        let descriptor = LibboxGetTunnelFileDescriptor()
        guard descriptor >= 0 else { throw AuraError.message("Системный TUN-дескриптор недоступен.") }
        ret0_.pointee = descriptor
    }

    func usePlatformAutoDetectControl() -> Bool { false }
    func autoDetectControl(_ fd: Int32) throws {}
    func useProcFS() -> Bool { false }
    func underNetworkExtension() -> Bool { true }
    func includeAllNetworks() -> Bool { false }
    func readWIFIState() -> LibboxWIFIState? { nil }
    func clearDNSCache() {}
    func writeLog(_ message: String?) { /* Core messages can contain credentials/addresses; not persisted. */ }
    func send(_ notification: LibboxNotification?) throws {}
    func findConnectionOwner(_ ipProtocol: Int32, sourceAddress: String?, sourcePort: Int32, destinationAddress: String?, destinationPort: Int32, ret0_: UnsafeMutablePointer<Int32>?) throws { throw AuraError.message("Определение приложения недоступно на iOS.") }
    func packageName(byUid uid: Int32, error: NSErrorPointer) -> String { "" }
    func uid(byPackageName name: String?, ret0_: UnsafeMutablePointer<Int32>?) throws { throw AuraError.message("Правила по приложениям недоступны на iOS.") }

    func startDefaultInterfaceMonitor(_ listener: LibboxInterfaceUpdateListenerProtocol?) throws {
        guard let listener else { throw AuraError.message("Нет слушателя сетевого интерфейса.") }
        let monitor = NWPathMonitor(); self.monitor = monitor
        let ready = DispatchSemaphore(value: 0)
        monitor.pathUpdateHandler = { path in
            let interface = path.availableInterfaces.first { path.usesInterfaceType($0.type) }
            listener.updateDefaultInterface(interface?.name ?? "", interfaceIndex: interface.map { Int32($0.index) } ?? -1, isExpensive: path.isExpensive, isConstrained: path.isConstrained)
            ready.signal()
        }
        monitor.start(queue: monitorQueue)
        guard ready.wait(timeout: .now() + 5) == .success else { closeMonitor(); throw AuraError.message("Не удалось определить сетевой интерфейс.") }
    }
    func closeDefaultInterfaceMonitor(_ listener: LibboxInterfaceUpdateListenerProtocol?) throws { closeMonitor() }
    func closeMonitor() { monitor?.cancel(); monitor = nil }
    func getInterfaces() throws -> LibboxNetworkInterfaceIteratorProtocol {
        guard let monitor else { throw AuraError.message("Монитор сети не запущен.") }
        let interfaces = monitor.currentPath.availableInterfaces.map { value -> LibboxNetworkInterface in
            let item = LibboxNetworkInterface()
            item.name = value.name; item.index = Int32(value.index); item.mtu = 1500
            switch value.type {
            case .wifi: item.type = LibboxInterfaceTypeWIFI
            case .cellular: item.type = LibboxInterfaceTypeCellular
            case .wiredEthernet: item.type = LibboxInterfaceTypeEthernet
            default: item.type = LibboxInterfaceTypeOther
            }
            return item
        }
        return InterfaceIterator(interfaces)
    }
}

private final class InterfaceIterator: NSObject, LibboxNetworkInterfaceIteratorProtocol {
    private let values: [LibboxNetworkInterface]
    private var index = 0
    init(_ values: [LibboxNetworkInterface]) { self.values = values }
    func hasNext() -> Bool { index < values.count }
    func next() -> LibboxNetworkInterface? { guard hasNext() else { return nil }; defer { index += 1 }; return values[index] }
}
#endif
