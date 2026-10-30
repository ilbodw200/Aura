import Foundation

enum ConfigBuilder {
    static func validate(_ profile: ProxyProfile) throws {
        guard !profile.name.isEmpty, profile.name.count <= 300, !profile.host.isEmpty,
              profile.host.count <= 253, !profile.host.contains(where: { $0.isWhitespace }),
              (1...65535).contains(profile.port) else { throw AuraError.message("Неверное название, адрес сервера или порт.") }
        if profile.kind != .socks && profile.credential.isEmpty { throw AuraError.message("В конфигурации нет ключа или пароля.") }
        if [.vless, .vmess].contains(profile.kind), UUID(uuidString: profile.credential) == nil { throw AuraError.message("Для этого протокола нужен корректный UUID.") }
        let transport = profile.query["type"] ?? "tcp"
        guard ["tcp", "ws", "grpc", "http"].contains(transport) else { throw AuraError.message("Транспорт \(transport) пока не поддерживается; профиль не был изменён.") }
        let security = profile.query["security"] ?? "none"
        guard ["none", "tls", "reality", ""].contains(security) else { throw AuraError.message("Неизвестный режим TLS: \(security).") }
        if security == "reality" && (profile.query["pbk"] ?? "").isEmpty { throw AuraError.message("Для Reality нужен публичный ключ pbk.") }
        if let flow = profile.query["flow"], !["", "xtls-rprx-vision"].contains(flow) { throw AuraError.message("Неподдерживаемый flow: \(flow).") }
        if let header = profile.query["headerType"], !["", "none"].contains(header) { throw AuraError.message("TCP headerType=\(header) пока не поддерживается.") }
        if let encoding = profile.query["packetEncoding"], !["xudp", "packetaddr"].contains(encoding) { throw AuraError.message("Неподдерживаемая упаковка UDP: \(encoding).") }
        let allowed: Set<String> = ["type", "security", "sni", "peer", "fp", "pbk", "sid", "flow", "path", "host", "serviceName", "alpn", "encryption", "net", "tls", "scy", "aid", "allowInsecure", "insecure", "obfs", "obfs-password", "spx", "headerType", "packetEncoding"]
        if let unknown = profile.query.keys.sorted().first(where: { !allowed.contains($0) }) { throw AuraError.message("Параметр \(unknown) пока не поддерживается. Чтобы не испортить конфигурацию, импорт остановлен.") }
        if let encryption = profile.query["encryption"], encryption != "none" { throw AuraError.message("VLESS encryption \(encryption) пока не поддерживается.") }
        if profile.kind == .shadowsocks && !["aes-128-gcm", "aes-256-gcm", "chacha20-ietf-poly1305", "2022-blake3-aes-128-gcm", "2022-blake3-aes-256-gcm", "2022-blake3-chacha20-poly1305"].contains(profile.method) { throw AuraError.message("Метод Shadowsocks пока не поддерживается.") }
    }

    static func build(profile: ProxyProfile, snapshot: AppSnapshot) throws -> Data {
        try validate(profile)
        guard !profile.isExample else { throw AuraError.message("Это пример оформления. Импортируйте собственный сервер.") }
        guard !["0.0.0.0", "::"].contains(profile.host) else {
            throw AuraError.message("Провайдер вернул служебный профиль вместо сервера. Включите «Отправлять HWID» в настройках подписки и обновите её. Если это не помогает, уточните привязку устройства у провайдера.")
        }
        var outbound: [String: Any] = ["type": profile.kind.rawValue, "tag": "proxy", "server": profile.host, "server_port": profile.port]
        switch profile.kind {
        case .vless: outbound["uuid"] = profile.credential; outbound["flow"] = profile.query["flow"] ?? ""
        case .vmess: outbound["uuid"] = profile.credential; outbound["security"] = profile.query["scy"] ?? "auto"; outbound["alter_id"] = Int(profile.query["aid"] ?? "0") ?? 0
        case .trojan, .hysteria2: outbound["password"] = profile.credential
        case .shadowsocks: outbound["method"] = profile.method; outbound["password"] = profile.credential
        case .socks: outbound["version"] = "5"; outbound["username"] = profile.username; outbound["password"] = profile.credential
        }
        if [.vless, .vmess].contains(profile.kind), let encoding = profile.query["packetEncoding"] { outbound["packet_encoding"] = encoding }
        let security = profile.query["security"] ?? (profile.kind == .trojan || profile.kind == .hysteria2 ? "tls" : "none")
        if security == "tls" || security == "reality" {
            var tls: [String: Any] = ["enabled": true, "server_name": profile.query["sni"] ?? profile.query["peer"] ?? profile.host]
            if profile.query["insecure"] == "1" || profile.query["allowInsecure"] == "1" { throw AuraError.message("Проверка TLS обязательна. Удалите insecure из конфигурации или исправьте сертификат сервера.") }
            if let alpn = profile.query["alpn"], !alpn.isEmpty { tls["alpn"] = alpn.split(separator: ",").map(String.init) }
            if let fingerprint = profile.query["fp"], !fingerprint.isEmpty { tls["utls"] = ["enabled": true, "fingerprint": fingerprint] }
            if security == "reality" { tls["reality"] = ["enabled": true, "public_key": profile.query["pbk"] ?? "", "short_id": profile.query["sid"] ?? ""] }
            outbound["tls"] = tls
        }
        let transport = profile.query["type"] ?? "tcp"
        if transport != "tcp" {
            var options: [String: Any] = ["type": transport]
            if transport == "grpc" { options["service_name"] = profile.query["serviceName"] ?? "" }
            else {
                options["path"] = profile.query["path"] ?? "/"
                if let host = profile.query["host"] {
                    if transport == "http" { options["host"] = host.split(separator: ",").map(String.init) }
                    else { options["headers"] = ["Host": host] }
                }
            }
            outbound["transport"] = options
        }
        if profile.kind == .hysteria2, let obfs = profile.query["obfs"], !obfs.isEmpty {
            guard obfs == "salamander", let password = profile.query["obfs-password"], !password.isEmpty else { throw AuraError.message("Поддерживается Hysteria2 obfs=salamander с паролем.") }
            outbound["obfs"] = ["type": obfs, "password": password]
        }
        var rules: [[String: Any]] = [["protocol": "dns", "action": "hijack-dns"]]
        if snapshot.mode == .smart {
            for rule in snapshot.rules where rule.enabled {
                try RoutingRule.validate(rule.value, kind: rule.kind)
                let key = switch rule.kind { case .domain: "domain"; case .suffix: "domain_suffix"; case .keyword: "domain_keyword"; case .cidr: "ip_cidr" }
                if rule.action == .block { rules.append([key: [rule.normalizedValue], "action": "reject"]) }
                else { rules.append([key: [rule.normalizedValue], "outbound": rule.action.rawValue]) }
            }
        }
        if snapshot.bypassLAN { rules.append(["ip_is_private": true, "outbound": "direct"]) }
        let final = snapshot.mode == .direct ? "direct" : "proxy"
        let config: [String: Any] = [
            "log": ["level": "warn", "timestamp": true],
            "dns": ["servers": [["tag": "secure", "address": snapshot.dns, "detour": final]], "strategy": "ipv4_only"],
            "inbounds": [["type": "tun", "tag": "tun-in", "address": ["172.19.0.1/30", "fdfe:dcba:9876::1/126"], "mtu": 1400, "auto_route": true, "stack": "gvisor", "sniff": true]],
            "outbounds": [outbound, ["type": "direct", "tag": "direct"]],
            "route": ["rules": rules, "final": final, "auto_detect_interface": true]
        ]
        return try JSONSerialization.data(withJSONObject: config, options: [.prettyPrinted, .sortedKeys, .withoutEscapingSlashes])
    }
}
