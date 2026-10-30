import Foundation

enum SubscriptionService {
    static func fetch(_ url: URL, metadata: DeviceMetadata? = nil) async throws -> [ProxyProfile] {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 20
        configuration.timeoutIntervalForResource = 30
        let session = URLSession(configuration: configuration, delegate: SecureRedirectDelegate(), delegateQueue: nil)
        defer { session.invalidateAndCancel() }
        let request = try SubscriptionRequest.make(url: url, metadata: metadata)
        let (bytes, response) = try await session.bytes(for: request)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode), http.url?.scheme == "https" else { throw AuraError.message("Сервер подписки вернул ошибку или небезопасное перенаправление.") }
        guard response.expectedContentLength <= 2_000_000 else { throw AuraError.message("Подписка превышает 2 МБ.") }
        var data = Data()
        for try await byte in bytes {
            data.append(byte)
            guard data.count <= 2_000_000 else { throw AuraError.message("Подписка превышает 2 МБ.") }
        }
        guard let text = String(data: data, encoding: .utf8) else { throw AuraError.message("Подписка должна содержать текст UTF-8.") }
        return try ProfileParser.parse(text)
    }
}

private final class SecureRedirectDelegate: NSObject, URLSessionTaskDelegate {
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) {
        completionHandler(SubscriptionRequest.redirect(request, from: response.url))
    }
}
