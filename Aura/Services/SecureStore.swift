import Foundation
import CryptoKit
import Security
import CommonCrypto

enum SecureStore {
    private static func key() throws -> SymmetricKey {
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: "app.aura.vault", kSecAttrAccount as String: "key"]
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query.merging([kSecReturnData as String: true]) { _, new in new } as CFDictionary, &item)
        if status == errSecSuccess, let data = item as? Data { return SymmetricKey(data: data) }
        guard status == errSecItemNotFound else { throw AuraError.message("Связка ключей недоступна (\(status)).") }
        let generated = SymmetricKey(size: .bits256)
        let data = generated.withUnsafeBytes { Data($0) }
        let insert = query.merging([kSecValueData as String: data, kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly]) { _, new in new }
        guard SecItemAdd(insert as CFDictionary, nil) == errSecSuccess else { throw AuraError.message("Не удалось сохранить ключ шифрования.") }
        return generated
    }
    private static func vaultURL() throws -> URL {
        let directory = try FileManager.default.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
        return directory.appendingPathComponent("vault.aura")
    }
    static func load() throws -> AppSnapshot {
        let url = try vaultURL()
        guard FileManager.default.fileExists(atPath: url.path) else { return AppSnapshot() }
        let bytes = try Data(contentsOf: url)
        let data = try AES.GCM.open(AES.GCM.SealedBox(combined: bytes), using: key())
        return try JSONDecoder().decode(AppSnapshot.self, from: data)
    }
    static func save(_ snapshot: AppSnapshot) throws {
        let sealed = try AES.GCM.seal(JSONEncoder().encode(snapshot), using: key())
        guard let data = sealed.combined else { throw AuraError.message("Ошибка шифрования.") }
        try data.write(to: vaultURL(), options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
    }
}

enum BackupService {
    private struct Envelope: Codable { var version: Int; var rounds: UInt32; var salt: Data; var ciphertext: Data }
    private static func derive(_ password: String, salt: Data, rounds: UInt32) throws -> SymmetricKey {
        var result = Data(count: 32)
        let passwordData = Array(password.utf8)
        let status = result.withUnsafeMutableBytes { output in
            salt.withUnsafeBytes { saltBytes in
                passwordData.withUnsafeBytes { passwordBytes in
                    CCKeyDerivationPBKDF(CCPBKDFAlgorithm(kCCPBKDF2), passwordBytes.baseAddress?.assumingMemoryBound(to: Int8.self), passwordBytes.count, saltBytes.baseAddress?.assumingMemoryBound(to: UInt8.self), salt.count, CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256), rounds, output.baseAddress?.assumingMemoryBound(to: UInt8.self), 32)
                }
            }
        }
        guard status == kCCSuccess else { throw AuraError.message("Не удалось создать ключ резервной копии.") }
        return SymmetricKey(data: result)
    }
    static func export(_ snapshot: AppSnapshot, password: String) throws -> Data {
        guard password.count >= 12 else { throw AuraError.message("Пароль резервной копии должен содержать минимум 12 символов.") }
        var salt = Data(count: 16)
        let status = salt.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault, 16, $0.baseAddress!) }
        guard status == errSecSuccess else { throw AuraError.message("Генератор случайных чисел недоступен.") }
        let key = try derive(password, salt: salt, rounds: 210_000)
        let sealed = try AES.GCM.seal(JSONEncoder().encode(snapshot), using: key)
        guard let ciphertext = sealed.combined else { throw AuraError.message("Ошибка шифрования.") }
        return try JSONEncoder().encode(Envelope(version: 1, rounds: 210_000, salt: salt, ciphertext: ciphertext))
    }
    static func restore(_ data: Data, password: String) throws -> AppSnapshot {
        guard data.count <= 8_000_000 else { throw AuraError.message("Резервная копия слишком большая.") }
        let envelope = try JSONDecoder().decode(Envelope.self, from: data)
        guard envelope.version == 1, envelope.rounds == 210_000, envelope.salt.count == 16 else { throw AuraError.message("Неизвестный формат резервной копии.") }
        let key = try derive(password, salt: envelope.salt, rounds: envelope.rounds)
        let plaintext = try AES.GCM.open(AES.GCM.SealedBox(combined: envelope.ciphertext), using: key)
        var snapshot = try JSONDecoder().decode(AppSnapshot.self, from: plaintext)
        guard snapshot.version == 1, snapshot.profiles.count <= 1000 else { throw AuraError.message("Несовместимая версия копии.") }
        guard Set(snapshot.profiles.map(\.id)).count == snapshot.profiles.count,
              Set(snapshot.subscriptions.map(\.id)).count == snapshot.subscriptions.count,
              Set(snapshot.rules.map(\.id)).count == snapshot.rules.count else { throw AuraError.message("В копии повторяются идентификаторы.") }
        for profile in snapshot.profiles { try ConfigBuilder.validate(profile) }
        for rule in snapshot.rules { try RoutingRule.validate(rule.value, kind: rule.kind) }
        for subscription in snapshot.subscriptions {
            guard subscription.url.scheme == "https", subscription.url.host != nil, subscription.url.user == nil, subscription.url.password == nil else { throw AuraError.message("В копии небезопасный адрес подписки.") }
        }
        guard ["https://1.1.1.1/dns-query", "https://8.8.8.8/dns-query", "https://9.9.9.9/dns-query"].contains(snapshot.dns) else { throw AuraError.message("В копии неизвестная настройка DNS.") }
        snapshot.appearance.cornerRadius = min(36, max(12, snapshot.appearance.cornerRadius))
        snapshot.appearance.widgets = HomeWidget.allCases.filter { snapshot.appearance.widgets.contains($0) }.sorted { lhs, rhs in
            (snapshot.appearance.widgets.firstIndex(of: lhs) ?? 0) < (snapshot.appearance.widgets.firstIndex(of: rhs) ?? 0)
        }
        if !snapshot.profiles.contains(where: { $0.id == snapshot.selectedID }) { snapshot.selectedID = snapshot.profiles.first?.id }
        return snapshot
    }
}
