import Foundation

enum ProxyProtocol: String, Codable, CaseIterable, Identifiable {
    case vless, vmess, trojan, shadowsocks, socks, hysteria2
    var id: String { rawValue }
    var title: String {
        switch self {
        case .shadowsocks: "Shadowsocks"
        case .socks: "SOCKS5"
        case .hysteria2: "Hysteria2"
        default: rawValue.uppercased()
        }
    }
}

struct ProxyProfile: Codable, Identifiable, Equatable {
    var id = UUID()
    var name: String
    var kind: ProxyProtocol
    var host: String
    var port: Int
    var credential: String
    var username: String = ""
    var method: String = "aes-128-gcm"
    var query: [String: String] = [:]
    var favorite = false
    var subscriptionID: UUID?
    var latencyMS: Int?
    var latencyCheckedAt: Date?
    var isExample = false
    var pingMethod: String?
    var pingStatus: Int?
    var subtitle: String { "\(kind.title) · \(host):\(port)" }
}

struct Subscription: Codable, Identifiable {
    var usage: SubscriptionUsage?
    var id = UUID()
    var name: String
    var url: URL
    var updatedAt: Date?
    var lastError: String?
    var sendHWID: Bool?
    var allowHTTP: Bool?
    var lastWarning: String?
}

enum RouteAction: String, Codable, CaseIterable, Identifiable {
    case proxy, direct, block
    var id: String { rawValue }
    var title: String {
        switch self { case .proxy: "Прокси"; case .direct: "Напрямую"; case .block: "Блокировать" }
    }
}

enum RuleKind: String, Codable, CaseIterable, Identifiable {
    case domain, suffix, keyword, cidr
    var id: String { rawValue }
    var title: String {
        switch self { case .domain: "Домен"; case .suffix: "Домен и поддомены"; case .keyword: "Часть домена"; case .cidr: "IPv4 / CIDR" }
    }
}

struct RoutingRule: Codable, Identifiable, Equatable {
    var id = UUID()
    var kind: RuleKind
    var value: String
    var action: RouteAction
    var enabled = true
    var normalizedValue: String { kind == .cidr ? value : value.lowercased().trimmingCharacters(in: CharacterSet(charactersIn: ". ")) }
    func matches(_ input: String) -> Bool {
        let host = input.lowercased().trimmingCharacters(in: CharacterSet(charactersIn: ". "))
        let needle = normalizedValue
        switch kind {
        case .domain: return host == needle
        case .suffix: return host == needle || host.hasSuffix("." + needle)
        case .keyword: return host.contains(needle)
        case .cidr: return Self.containsIPv4(host, in: value)
        }
    }
    static func ipv4(_ value: String) -> UInt32? {
        let octets = value.split(separator: ".", omittingEmptySubsequences: false)
        guard octets.count == 4 else { return nil }
        return octets.reduce(Optional(UInt32(0))) { result, item in
            guard let result, !item.isEmpty, item.allSatisfy({ $0.isASCII && $0.isNumber }), let byte = UInt8(item) else { return nil }
            return (result << 8) | UInt32(byte)
        }
    }
    static func containsIPv4(_ host: String, in cidr: String) -> Bool {
        let parts = cidr.split(separator: "/", omittingEmptySubsequences: false)
        guard parts.count == 2, let bits = Int(parts[1]), (0...32).contains(bits),
              let ip = ipv4(host), let network = ipv4(String(parts[0])) else { return false }
        let mask: UInt32 = bits == 0 ? 0 : UInt32.max << (32 - bits)
        return ip & mask == network & mask
    }
    static func validate(_ value: String, kind: RuleKind) throws {
        if kind == .cidr {
            guard let ip = value.split(separator: "/").first, containsIPv4(String(ip), in: value) else { throw AuraError.message("Введите IPv4/CIDR, например 192.168.0.0/16.") }
        } else {
            guard !value.isEmpty, !value.contains(where: { $0.isWhitespace }), !value.contains("://"), !value.contains("/"), value != "." else {
                throw AuraError.message("Укажите домен без схемы, пути и пробелов.")
            }
        }
    }
}

enum RoutingMode: String, Codable, CaseIterable, Identifiable {
    case smart, global, direct
    var id: String { rawValue }
    var title: String { switch self { case .smart: "По правилам"; case .global: "Весь трафик"; case .direct: "Напрямую" } }
}

enum HomeWidget: String, Codable, CaseIterable, Identifiable {
    case server, routing, favorites
    var id: String { rawValue }
    var title: String { switch self { case .server: "Группы серверов"; case .routing: "Маршрутизация"; case .favorites: "Избранное" } }
}

struct Appearance: Codable, Equatable {
    var accent = "mint"
    var backdrop = "aurora"
    var colorScheme = "dark"
    var compact = false
    var reduceMotion = false
    var showHost = true
    var cornerRadius: Double = 28
    var widgets = HomeWidget.allCases
    var hiddenWidgets: [HomeWidget] = []
}

struct AppSnapshot: Codable {
    var ping: PingSettings?
    var version = 1
    var profiles: [ProxyProfile] = []
    var subscriptions: [Subscription] = []
    var selectedID: UUID?
    var rules: [RoutingRule] = []
    var mode = RoutingMode.smart
    var appearance = Appearance()
    var dns = "https://1.1.1.1/dns-query"
    var bypassLAN = true
    var onDemand = false
    var hideInSwitcher = true
    var sendHWID: Bool?
}

enum AuraError: LocalizedError {
    case message(String)
    var errorDescription: String? { switch self { case .message(let text): text } }
}

enum SharedContainer {
    static var group: String { Bundle.main.object(forInfoDictionaryKey: "AuraAppGroup") as? String ?? "group.app.aura.proxy" }
    static func directory() throws -> URL {
        guard let url = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: group) else {
            throw AuraError.message("App Group недоступна. Проверьте подпись приложения и расширения.")
        }
        return url
    }
}
