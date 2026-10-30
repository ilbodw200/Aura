import Foundation

struct DeviceMetadata: Equatable {
    var hwid: String
    var os: String
    var osVersion: String
    var model: String
    var deviceInfo: String
}

enum SubscriptionRequest {
    static let identityHeaders = ["X-HWID", "X-Device-OS", "X-Ver-OS", "X-Device-Model", "X-Device-Info"]
    static func make(url: URL, metadata: DeviceMetadata? = nil) throws -> URLRequest {
        guard url.scheme?.lowercased() == "https", url.host != nil, url.user == nil, url.password == nil else {
            throw AuraError.message("Подписка должна быть HTTPS-ссылкой без логина и пароля в адресе.")
        }
        var request = URLRequest(url: url)
        request.setValue("Aura/0.1.1", forHTTPHeaderField: "User-Agent")
        if let metadata {
            let values = [metadata.hwid, metadata.os, metadata.osVersion, metadata.model, metadata.deviceInfo]
            for (name, value) in zip(identityHeaders, values) {
                guard !value.contains("\r"), !value.contains("\n"), value.utf8.count <= 512 else { throw AuraError.message("Некорректные данные устройства.") }
                request.setValue(value, forHTTPHeaderField: name)
            }
        }
        return request
    }
    static func redirect(_ request: URLRequest, from source: URL?) -> URLRequest? {
        guard let destination = request.url, destination.scheme?.lowercased() == "https", destination.user == nil, destination.password == nil else { return nil }
        var redirected = request
        if source?.host?.lowercased() != destination.host?.lowercased() || (source?.port ?? 443) != (destination.port ?? 443) {
            for header in identityHeaders { redirected.setValue(nil, forHTTPHeaderField: header) }
        }
        return redirected
    }
}
