import Foundation

/// Uses the system VPN configuration instead of an App Group shared container.
enum TunnelPayload {
    static let key = "aura.configuration.v1"
    static func configuration(options: [String: NSObject]?, saved: [String: Any]?) throws -> String {
        guard let text = (options?[key] as? String) ?? (saved?[key] as? String) else {
            throw AuraError.message("Нет конфигурации VPN. Откройте AURA и подключитесь заново.")
        }
        let data = Data(text.utf8)
        guard data.count <= 2_000_000,
              let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let inbounds = json["inbounds"] as? [[String: Any]], !inbounds.isEmpty,
              let outbounds = json["outbounds"] as? [[String: Any]], !outbounds.isEmpty else {
            throw AuraError.message("Повреждённая конфигурация VPN. Выберите сервер и повторите подключение.")
        }
        return text
    }
}
