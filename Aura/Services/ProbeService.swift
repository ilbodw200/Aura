import Foundation
#if canImport(Libbox)
import Libbox
#endif

enum ProbeService {
    static func measure(profile: ProxyProfile, snapshot: AppSnapshot, settings: PingSettings) async throws -> PingResult {
        try settings.validate()
        let config = settings.method.viaProxy ? String(decoding: try ConfigBuilder.build(profile: profile, snapshot: snapshot), as: UTF8.self) : "{}"
        return try await Task.detached(priority: .utility) {
            #if canImport(Libbox)
            var error: NSError?
            let json = LibboxAuraProbe(config, profile.host, Int32(profile.port), settings.url, settings.method.rawValue, settings.mode.rawValue, Int32(settings.timeout * 1000), &error)
            guard error == nil, let json else { throw AuraError.message("Тест не завершён. Проверьте сервер, сеть и таймаут; ICMP может блокироваться.") }
            return try JSONDecoder().decode(PingResult.self, from: Data(json.utf8))
            #else
            throw AuraError.message("Для проверки нужна полная сборка AURA с VPN-ядром.")
            #endif
        }.value
    }
}
