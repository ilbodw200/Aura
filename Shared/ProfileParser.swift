import Foundation

enum ProfileParser {
    static func decodeBase64(_ value: String) -> Data? {
        var text = value.replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        text += String(repeating: "=", count: (4 - text.count % 4) % 4)
        return Data(base64Encoded: text)
    }

    /// The entire batch is validated before anything is persisted.
    static func parse(_ input: String) throws -> [ProxyProfile] {
        var text = input.trimmingCharacters(in: .whitespacesAndNewlines)
        if !text.contains("://"), let decoded = decodeBase64(text.filter { !$0.isWhitespace }), let string = String(data: decoded, encoding: .utf8) { text = string }
        let lines = text.components(separatedBy: .newlines).map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty && !$0.hasPrefix("#") }
        guard !lines.isEmpty, lines.count <= 1000 else { throw AuraError.message("Нужны ссылки конфигураций; максимум 1000 за один импорт.") }
        return try lines.enumerated().map { index, line in
            do { return try parseLink(line) }
            catch { throw AuraError.message("Строка \(index + 1): \(error.localizedDescription)") }
        }
    }

    static func parseLink(_ link: String) throws -> ProxyProfile {
        guard let scheme = link.components(separatedBy: "://").first?.lowercased() else { throw AuraError.message("Неверная ссылка.") }
        if scheme == "happ" { throw AuraError.message("Фирменные зашифрованные ссылки Happ пока не поддерживаются. Нужна стандартная ссылка от провайдера.") }
        if scheme == "vmess" { return try vmess(link) }
        if scheme == "ss" { return try shadowsocks(link) }
        let kind: ProxyProtocol
        switch scheme {
        case "vless": kind = .vless
        case "trojan": kind = .trojan
        case "socks", "socks5": kind = .socks
        case "hy2", "hysteria2": kind = .hysteria2
        default: throw AuraError.message("Неподдерживаемый формат. Используйте VLESS, VMess, Trojan, SS, SOCKS5 или Hysteria2.")
        }
        guard let components = URLComponents(string: link), let host = components.host, !host.isEmpty,
              let port = components.port, (1...65535).contains(port) else { throw AuraError.message("Проверьте адрес сервера и порт 1–65535.") }
        var query: [String: String] = [:]
        for item in components.queryItems ?? [] { query[item.name] = item.value ?? "" }
        let user = components.user ?? ""
        var profile = ProxyProfile(name: components.fragment?.isEmpty == false ? components.fragment! : host, kind: kind, host: host.trimmingCharacters(in: CharacterSet(charactersIn: "[]")), port: port, credential: kind == .socks ? components.password ?? "" : user, query: query)
        if kind == .socks { profile.username = user }
        if kind != .socks && user.isEmpty { throw AuraError.message("В ссылке нет ключа или пароля.") }
        if kind == .vless && UUID(uuidString: user) == nil { throw AuraError.message("VLESS требует корректный UUID.") }
        try ConfigBuilder.validate(profile)
        return profile
    }

    private static func vmess(_ link: String) throws -> ProxyProfile {
        let payload = String(link.dropFirst("vmess://".count))
        guard let data = decodeBase64(payload), let object = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let host = object["add"] as? String, !host.isEmpty, let uuid = object["id"] as? String, UUID(uuidString: uuid) != nil,
              let port = Int(String(describing: object["port"] ?? "")), (1...65535).contains(port) else { throw AuraError.message("Неверный VMess JSON.") }
        var query: [String: String] = [:]
        for key in ["net", "tls", "sni", "host", "path", "scy", "aid"] { if let value = object[key] { query[key] = String(describing: value) } }
        query["type"] = query["net"] ?? "tcp"
        query["security"] = query["tls"] == "tls" ? "tls" : "none"
        let profile = ProxyProfile(name: object["ps"] as? String ?? host, kind: .vmess, host: host, port: port, credential: uuid, query: query)
        try ConfigBuilder.validate(profile)
        return profile
    }

    private static func shadowsocks(_ link: String) throws -> ProxyProfile {
        var raw = String(link.dropFirst(5))
        let fragment = raw.components(separatedBy: "#").dropFirst().joined(separator: "#").removingPercentEncoding
        raw = raw.components(separatedBy: "#")[0]
        if !raw.contains("@"), let data = decodeBase64(raw), let decoded = String(data: data, encoding: .utf8) { raw = decoded }
        guard let at = raw.lastIndex(of: "@") else { throw AuraError.message("Неверная Shadowsocks-ссылка.") }
        var auth = String(raw[..<at])
        if !auth.contains(":"), let data = decodeBase64(auth), let decoded = String(data: data, encoding: .utf8) { auth = decoded }
        guard let colon = auth.firstIndex(of: ":"), let endpoint = URLComponents(string: "ss://" + String(raw[raw.index(after: at)...])),
              let host = endpoint.host, let port = endpoint.port, (1...65535).contains(port) else { throw AuraError.message("Неверные параметры Shadowsocks.") }
        guard !(endpoint.queryItems ?? []).contains(where: { $0.name == "plugin" }) else { throw AuraError.message("Shadowsocks plugins пока не поддерживаются.") }
        let name = fragment.flatMap { $0.isEmpty ? nil : $0 } ?? host
        let profile = ProxyProfile(name: name, kind: .shadowsocks, host: host, port: port, credential: String(auth[auth.index(after: colon)...]).removingPercentEncoding ?? "", method: String(auth[..<colon]))
        try ConfigBuilder.validate(profile)
        return profile
    }
}
