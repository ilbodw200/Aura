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
    static func validate(url: URL, allowHTTP: Bool = false) throws {
        let scheme = url.scheme?.lowercased()
        guard (scheme == "https" || (scheme == "http" && allowHTTP)), let host = url.host, !host.isEmpty, url.user == nil, url.password == nil else {
            throw AuraError.message("Нужна HTTPS-ссылка. HTTP разрешается только после предупреждения и дополнительной проверки.")
        }
    }
    static func validateForSaving(url: URL, allowHTTP: Bool, verifiedURL: URL?, profileCount: Int) throws {
        try validate(url: url, allowHTTP: allowHTTP)
        if url.scheme?.lowercased() == "http" {
            guard allowHTTP, verifiedURL == url, profileCount > 0 else {
                throw AuraError.message("Для HTTP необходимо принять предупреждение и успешно проверить доступность и формат этой ссылки.")
            }
        }
    }
    static func make(url: URL, metadata: DeviceMetadata? = nil, allowHTTP: Bool = false) throws -> URLRequest {
        try validate(url: url, allowHTTP: allowHTTP)
        var request = URLRequest(url: url)
        request.setValue("Aura/1.0.1", forHTTPHeaderField: "User-Agent")
        request.setValue("text/plain, application/json;q=0.9, */*;q=0.5", forHTTPHeaderField: "Accept")
        if let metadata, url.scheme?.lowercased() == "https" {
            let values = [metadata.hwid, metadata.os, metadata.osVersion, metadata.model, metadata.deviceInfo]
            for (name, value) in zip(identityHeaders, values) {
                guard !value.contains("\r"), !value.contains("\n"), value.utf8.count <= 512 else { throw AuraError.message("Некорректные данные устройства.") }
                request.setValue(value, forHTTPHeaderField: name)
            }
        }
        return request
    }
    static func redirect(_ request: URLRequest, from source: URL?, allowHTTP: Bool = false) -> URLRequest? {
        guard let destination = request.url else { return nil }
        let isHTTP = destination.scheme?.lowercased() == "http"
        guard (try? validate(url: destination, allowHTTP: allowHTTP && source?.scheme?.lowercased() == "http")) != nil else { return nil }
        var redirected = request
        let sourcePort = source?.port ?? (source?.scheme == "http" ? 80 : 443)
        let destinationPort = destination.port ?? (isHTTP ? 80 : 443)
        if isHTTP || source?.host?.lowercased() != destination.host?.lowercased() || sourcePort != destinationPort {
            for header in identityHeaders { redirected.setValue(nil, forHTTPHeaderField: header) }
        }
        return redirected
    }
}
