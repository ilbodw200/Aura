import Foundation

enum SubscriptionService {
    static func fetch(_ url: URL) async throws -> [ProxyProfile] {
        guard url.scheme?.lowercased() == "https", url.host != nil, url.user == nil, url.password == nil else { throw AuraError.message("Подписка должна быть HTTPS-ссылкой без логина и пароля в адресе.") }
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 20
        configuration.timeoutIntervalForResource = 30
        let session = URLSession(configuration: configuration, delegate: SecureRedirectDelegate(), delegateQueue: nil)
        defer { session.invalidateAndCancel() }
        var request = URLRequest(url: url)
        request.setValue("Aura/0.1", forHTTPHeaderField: "User-Agent")
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
        guard request.url?.scheme?.lowercased() == "https", request.url?.user == nil, request.url?.password == nil else { completionHandler(nil); return }
        completionHandler(request)
    }
}
