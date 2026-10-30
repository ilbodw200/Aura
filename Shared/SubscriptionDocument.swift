import Foundation

struct SubscriptionContents {
    var profiles: [ProxyProfile]
    var warnings: [String]
    var skippedCount: Int = 0
}

enum SubscriptionDocument {
    static func parse(_ input: String) throws -> SubscriptionContents {
        var text = input.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: "\u{feff}", with: "")
        if !text.contains("://"), !text.hasPrefix("{"), !text.hasPrefix("["), let bytes = ProfileParser.decodeBase64(text.filter { !$0.isWhitespace }), let decoded = String(data: bytes, encoding: .utf8) { text = decoded.trimmingCharacters(in: .whitespacesAndNewlines) }
        if text.lowercased().hasPrefix("<!doctype") || text.lowercased().hasPrefix("<html") {
            throw AuraError.message("Загрузка выполнена, но сервер вернул HTML-страницу вместо серверов. Он может выбирать формат по клиенту или показывать страницу защиты. Нужен ответ со списком конфигураций.")
        }
        if text.hasPrefix("{") || text.hasPrefix("[") { return try parseJSON(text) }
        if text.hasPrefix("proxies:") || text.contains("\nproxies:") { throw AuraError.message("Сервер вернул Clash YAML. Для этой версии AURA нужен список ссылок либо Xray/sing-box JSON.") }
        let lines = text.components(separatedBy: .newlines).map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty && !$0.hasPrefix("#") }
        guard !lines.isEmpty, lines.count <= 1000 else { throw AuraError.message("Сервер вернул пустой или слишком большой список конфигураций.") }
        var profiles: [ProxyProfile] = [], warnings: [String] = []
        for (index, line) in lines.enumerated() {
            do { profiles.append(try ProfileParser.parseLink(line)) }
            catch { warnings.append("Строка \(index + 1): \(error.localizedDescription)") }
        }
        guard !profiles.isEmpty else { throw AuraError.message("Ответ сервера получен, но AURA не смогла разобрать конфигурации. " + warnings.prefix(3).joined(separator: " ")) }
        return SubscriptionContents(profiles: profiles, warnings: warnings, skippedCount: warnings.count)
    }
    private static func parseJSON(_ text: String) throws -> SubscriptionContents {
        guard let object = try? JSONSerialization.jsonObject(with: Data(text.utf8)) else { throw AuraError.message("Ответ получен в формате JSON, но JSON повреждён.") }
        let documents = (object as? [Any]) ?? [object]
        var profiles: [ProxyProfile] = [], warnings: [String] = []
        for (index, document) in documents.enumerated() {
            if let link = document as? String {
                do { profiles.append(try ProfileParser.parseLink(link)) } catch { warnings.append("Запись \(index + 1): \(error.localizedDescription)") }; continue
            }
            guard let config = document as? [String: Any] else { warnings.append("Запись \(index + 1): неизвестная структура."); continue }
            let outbounds = (config["outbounds"] as? [[String: Any]]) ?? [config]
            for outbound in outbounds {
                let type = outbound["protocol"] as? String ?? outbound["type"] as? String ?? ""
                if ["direct", "block", "dns", "freedom", "blackhole", "selector", "urltest"].contains(type) { continue }
                do { profiles.append(try jsonProfile(outbound, name: config["remarks"] as? String)) }
                catch { warnings.append("Запись \(index + 1): \(error.localizedDescription)") }
            }
        }
        guard !profiles.isEmpty else { throw AuraError.message("JSON получен, но совместимых серверов нет. " + warnings.prefix(3).joined(separator: " ")) }
        guard profiles.count <= 1000 else { throw AuraError.message("В JSON больше 1000 серверов.") }
        let skippedCount = warnings.count
        if documents.contains(where: { ($0 as? [String: Any])?["outbounds"] != nil }) { warnings.append("Из JSON импортированы серверы. Остальная конфигурация провайдера, включая маршрутизацию, не перенесена; используются настройки AURA.") }
        return SubscriptionContents(profiles: profiles, warnings: warnings, skippedCount: skippedCount)
    }
    private static func jsonProfile(_ object: [String: Any], name: String?) throws -> ProxyProfile {
        let isXray = object["protocol"] != nil
        let rawType = object[isXray ? "protocol" : "type"] as? String ?? ""
        guard let kind = ProxyProtocol(rawValue: rawType) else { throw AuraError.message("Протокол \(rawType) пока не поддерживается.") }
        let settings = object["settings"] as? [String: Any] ?? [:]
        let servers = (settings["vnext"] as? [[String: Any]]) ?? (settings["servers"] as? [[String: Any]]) ?? []
        guard !isXray || servers.count == 1 else { throw AuraError.message("Xray outbound должен содержать один сервер; многосерверный балансировщик не импортирован.") }
        let endpoint = isXray ? (servers.first ?? [:]) : object
        let user = (endpoint["users"] as? [[String: Any]])?.first ?? [:]
        let host = endpoint[isXray ? "address" : "server"] as? String ?? ""
        let port = (endpoint[isXray ? "port" : "server_port"] as? Int) ?? Int(endpoint[isXray ? "port" : "server_port"] as? String ?? "") ?? 0
        var query: [String: String] = [:]
        func set(_ key: String, _ value: Any?) { if let value { if let string = value as? String { query[key] = string } else if let array = value as? [String] { query[key] = array.joined(separator: ",") } } }
        let stream = object["streamSettings"] as? [String: Any] ?? [:]
        let transport = isXray ? stream["network"] as? String ?? "tcp" : (object["transport"] as? [String: Any])?["type"] as? String ?? "tcp"
        query["type"] = transport == "raw" ? "tcp" : transport
        if isXray {
            set("security", stream["security"])
            let tls = (stream["realitySettings"] as? [String: Any]) ?? (stream["tlsSettings"] as? [String: Any]) ?? [:]
            set("sni", tls["serverName"]); set("fp", tls["fingerprint"]); set("pbk", tls["publicKey"]); set("sid", tls["shortId"]); set("alpn", tls["alpn"])
            if tls["allowInsecure"] as? Bool == true { query["insecure"] = "1" }
            if tls["mldsa65Verify"] != nil || tls["pqv"] != nil { throw AuraError.message("Этот JSON требует новых параметров Reality, отсутствующих в текущем ядре.") }
            let options = stream["\(transport == "raw" ? "tcp" : transport)Settings"] as? [String: Any] ?? [:]
            set("path", options["path"]); set("serviceName", options["serviceName"])
            let headers = options["headers"] as? [String: Any] ?? [:]; set("host", headers["Host"] ?? options["host"])
            if let header = options["header"] as? [String: Any] { set("headerType", header["type"]) }
            set("flow", user["flow"])
        } else {
            if let tls = object["tls"] as? [String: Any], tls["enabled"] as? Bool == true {
                query["security"] = "tls"; set("sni", tls["server_name"]); set("alpn", tls["alpn"])
                if tls["insecure"] as? Bool == true { query["insecure"] = "1" }
                if let utls = tls["utls"] as? [String: Any] { set("fp", utls["fingerprint"]) }
                if let reality = tls["reality"] as? [String: Any], reality["enabled"] as? Bool == true { query["security"] = "reality"; set("pbk", reality["public_key"]); set("sid", reality["short_id"]) }
            }
            let options = object["transport"] as? [String: Any] ?? [:]; set("path", options["path"]); set("serviceName", options["service_name"])
            let headers = options["headers"] as? [String: Any] ?? [:]; set("host", headers["Host"] ?? options["host"]); set("flow", object["flow"]); set("packetEncoding", object["packet_encoding"])
            if let obfs = object["obfs"] as? [String: Any] { set("obfs", obfs["type"]); set("obfs-password", obfs["password"]) }
        }
        let credential: String
        switch kind {
        case .vless, .vmess: credential = (isXray ? user["id"] : object["uuid"]) as? String ?? ""
        case .socks: credential = (isXray ? user["pass"] : object["password"]) as? String ?? ""
        default: credential = endpoint["password"] as? String ?? ""
        }
        if kind == .vmess { set("scy", isXray ? user["security"] : object["security"]); if let alterID = (isXray ? user["alterId"] : object["alter_id"]) as? Int { query["aid"] = String(alterID) } }
        var profile = ProxyProfile(name: name ?? object["tag"] as? String ?? host, kind: kind, host: host, port: port, credential: credential, username: (isXray ? user["user"] : object["username"]) as? String ?? "", method: endpoint["method"] as? String ?? "aes-128-gcm", query: query)
        if profile.name.isEmpty { profile.name = host }
        try ConfigBuilder.validate(profile)
        return profile
    }
}
