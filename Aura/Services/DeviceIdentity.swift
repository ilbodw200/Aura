import Foundation
import UIKit
import Security

@MainActor enum DeviceIdentity {
    static func identifier() throws -> String {
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: "app.aura.device", kSecAttrAccount as String: "hwid"]
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query.merging([kSecReturnData as String: true]) { _, new in new } as CFDictionary, &item)
        if status == errSecSuccess, let data = item as? Data, let value = String(data: data, encoding: .utf8), !value.isEmpty { return value }
        guard status == errSecItemNotFound else { throw AuraError.message("Не удалось прочитать HWID из Связки ключей.") }
        let value = UUID().uuidString.lowercased()
        let insert = query.merging([kSecValueData as String: Data(value.utf8), kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly]) { _, new in new }
        guard SecItemAdd(insert as CFDictionary, nil) == errSecSuccess else { throw AuraError.message("Не удалось сохранить HWID.") }
        return value
    }
    static func metadata() throws -> DeviceMetadata {
        let device = UIDevice.current
        // App-scoped installation identifier: no serial number, IMEI or advertising ID.
        return DeviceMetadata(hwid: try identifier(), os: "iOS", osVersion: device.systemVersion, model: device.model, deviceInfo: "AURA; \(device.model); iOS \(device.systemVersion)")
    }
}
