import Foundation

enum SubscriptionService {
    static func fetch(_ url: URL, metadata: DeviceMetadata? = nil, allowHTTP: Bool = false) async throws -> SubscriptionContents {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 20
        configuration.timeoutIntervalForResource = 30
        let session = URLSession(configuration: configuration, delegate: SecureRedirectDelegate(allowHTTP: allowHTTP), delegateQueue: nil)
        defer { session.invalidateAndCancel() }
        let request = try SubscriptionRequest.make(url: url, metadata: metadata, allowHTTP: allowHTTP)
        let (bytes, response) = try await session.bytes(for: request)
        guard let http = response as? HTTPURLResponse else { throw AuraError.message("Сервер не вернул HTTP-ответ.") }
        guard (200...299).contains(http.statusCode) else {
            let detail: String
            switch http.statusCode {
            case 401, 403: detail = "Сервер отказал в доступе. Возможны ограничения токена, HWID или выбранного клиента."
            case 404: detail = "Сервер не нашёл этот ресурс."
            case 429: detail = "Превышен лимит запросов. Обновите подписку позже."
            case 300...399: detail = "Перенаправление заблокировано. Переход с HTTPS на HTTP не разрешён."
            case 500...599: detail = "Ошибка на стороне сервера подписки."
            default: detail = "Не удалось загрузить список серверов."
            }
            throw AuraError.message("HTTP \(http.statusCode). \(detail)")
        }
        guard response.expectedContentLength <= 2_000_000 else { throw AuraError.message("Подписка превышает 2 МБ.") }
        var data = Data()
        for try await byte in bytes {
            data.append(byte)
            guard data.count <= 2_000_000 else { throw AuraError.message("Подписка превышает 2 МБ.") }
        }
        guard let text = String(data: data, encoding: .utf8) else { throw AuraError.message("Подписка должна содержать текст UTF-8.") }
        return try SubscriptionDocument.parse(text)
    }
    static func message(for error: Error) -> String {
        if let error = error as? AuraError { return error.localizedDescription }
        if let error = error as? URLError {
            switch error.code {
            case .timedOut: return "Сервер подписки не ответил вовремя. Ссылка сохранена; повторите обновление позже."
            case .notConnectedToInternet, .networkConnectionLost: return "Нет подключения к сети или соединение оборвалось."
            case .cannotFindHost, .dnsLookupFailed: return "Не удалось разрешить DNS-имя сервера подписки."
            case .serverCertificateUntrusted, .serverCertificateHasBadDate, .serverCertificateHasUnknownRoot, .serverCertificateNotYetValid, .secureConnectionFailed: return "iOS не приняла TLS-сертификат или не смогла установить защищённое соединение. Проверка сертификата не отключена."
            case .cannotConnectToHost: return "Не удалось соединиться с сервером подписки."
            default: return "Сетевая ошибка \(error.errorCode). Повторите обновление подписки."
            }
        }
        return "Не удалось обработать ответ сервера. Предыдущие данные сохранены."
    }
}

private final class SecureRedirectDelegate: NSObject, URLSessionTaskDelegate {
    let allowHTTP: Bool
    init(allowHTTP: Bool) { self.allowHTTP = allowHTTP }
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) {
        completionHandler(SubscriptionRequest.redirect(request, from: response.url, allowHTTP: allowHTTP))
    }
}
