import Foundation

/// Supported, explicit mappings for provider URI extensions. Never drop an unknown mask.
enum ProviderOptions {
    static func fragment(_ profile: ProxyProfile) throws -> [String: Int]? {
        let q = profile.query
        if let mode = q["mode"], !mode.isEmpty {
            guard q["type"] == "grpc", mode == "gun" else { throw AuraError.message("Поддерживается gRPC mode=gun. Другой режим не был заменён.") }
        }
        if let authority = q["authority"], !authority.isEmpty {
            guard q["type"] == "grpc", authority.utf8.count <= 253, !authority.contains(where: { $0.isWhitespace || $0 == "/" || $0 == "@" }) else { throw AuraError.message("Некорректный gRPC authority.") }
        }
        guard let value = q["fm"], !value.isEmpty else { return nil }
        guard value.utf8.count <= 8192, let data = value.data(using: .utf8), let mask = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { throw AuraError.message("Параметр fm должен содержать JSON FinalMask.") }
        if mask.isEmpty { return nil }
        if profile.kind == .hysteria2 {
            guard Set(mask.keys) == ["quicParams"], let quic = mask["quicParams"] as? [String: Any], Set(quic.keys).isSubset(of: ["congestion", "debug"]), quic["congestion"] as? String == "bbr", (quic["debug"] == nil || quic["debug"] as? Bool == false) else { throw AuraError.message("Для Hysteria2 поддерживается fm.quicParams с BBR и выключенной отладкой.") }
            // sing-box Hysteria2 selects BBR when no fixed upload bandwidth is set.
            return nil
        }
        guard Set(mask.keys) == ["tcp"], let tcp = mask["tcp"] as? [[String: Any]], tcp.count == 1, Set(tcp[0].keys) == ["type", "settings"], tcp[0]["type"] as? String == "fragment", let settings = tcp[0]["settings"] as? [String: Any], Set(settings.keys).isSubset(of: ["packets", "length", "delay"]), settings["packets"] as? String == "tlshello", ["tls", "reality"].contains(q["security"] ?? (profile.kind == .trojan ? "tls" : "none")) else { throw AuraError.message("Этот fm пока не поддерживается. Доступна TCP-фрагментация tlshello с length/delay; маска не была удалена.") }
        let length = try range(settings["length"], minimum: 1, maximum: 16384)
        let delay = try range(settings["delay"], minimum: 0, maximum: 100)
        return ["length_min": length.0, "length_max": length.1, "delay_min": delay.0, "delay_max": delay.1]
    }
    private static func range(_ value: Any?, minimum: Int, maximum: Int) throws -> (Int, Int) {
        guard let text = value as? String, text.utf8.count <= 20 else { throw AuraError.message("В fm нужны строковые диапазоны length и delay.") }
        let pieces = text.split(separator: "-", omittingEmptySubsequences: false)
        guard (1...2).contains(pieces.count), pieces.allSatisfy({ !$0.isEmpty && $0.allSatisfy({ $0.isASCII && $0.isNumber }) }), let low = Int(pieces[0]), let high = Int(pieces.last!), minimum <= low, low <= high, high <= maximum else { throw AuraError.message("Недопустимый диапазон fm: length 1–16384, delay 0–100 мс.") }
        return (low, high)
    }
}
