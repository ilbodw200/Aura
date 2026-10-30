import Foundation

enum PingMethod: String, Codable, CaseIterable, Identifiable {
    case get = "GET", head = "HEAD", tcp = "TCP", icmp = "ICMP"
    var id: String { rawValue }
    var title: String { self == .get || self == .head ? "Через прокси (\(rawValue))" : rawValue }
    var viaProxy: Bool { self == .get || self == .head }
}
enum PingMode: String, Codable, CaseIterable, Identifiable {
    case `default`, double, keepalive
    var id: String { rawValue }
}
struct PingSettings: Codable, Equatable {
    var method = PingMethod.get
    var mode = PingMode.default
    var timeout = 7.0
    var url = "https://www.gstatic.com/generate_204"
    var displayStatus = false
    func validate() throws {
        guard (5...15).contains(timeout), timeout.isFinite else { throw AuraError.message("Таймаут должен быть от 5 до 15 секунд.") }
        if method.viaProxy {
            guard let value = URLComponents(string: url), ["https", "http"].contains(value.scheme?.lowercased() ?? ""), value.host?.isEmpty == false, value.user == nil, value.password == nil else { throw AuraError.message("Введите HTTP/HTTPS URL теста без логина и пароля.") }
        }
    }
}
struct PingResult: Codable {
    let ms: Int
    let status: Int
    let attempts: Int
    let reused: Bool
}
