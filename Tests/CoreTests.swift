import XCTest
@testable import Aura

final class CoreTests: XCTestCase {
    private let uuid = "11111111-2222-4333-8444-555555555555"

    func testSubscriptionUsageDoesNotInventLimits() {
        XCTAssertNil(SubscriptionUsage.parse(nil))
        XCTAssertNil(SubscriptionUsage.parse("upload=-5; download=abc"))
        let value = SubscriptionUsage.parse("upload=100; download=200; total=0; expire=1900000000")
        XCTAssertEqual(value?.used, 300)
        XCTAssertEqual(value?.total, 0)
        XCTAssertNotNil(value?.expire)
        XCTAssertTrue(value?.summary?.contains("∞") == true)
        XCTAssertNil(SubscriptionUsage.parse("upload=9223372036854775807; download=1")?.used)
    }

    func testPingSettingsMigrationAndValidation() throws {
        let old = AppSnapshot()
        var object = try XCTUnwrap(JSONSerialization.jsonObject(with: JSONEncoder().encode(old)) as? [String: Any])
        object.removeValue(forKey: "ping")
        let restored = try JSONDecoder().decode(AppSnapshot.self, from: JSONSerialization.data(withJSONObject: object))
        XCTAssertNil(restored.ping)
        var settings = PingSettings()
        try settings.validate()
        for bad in ["file:///tmp/test", "https://user:secret@example.com", "https://"] {
            settings.url = bad
            XCTAssertThrowsError(try settings.validate())
        }
        settings = PingSettings(); settings.timeout = 16
        XCTAssertThrowsError(try settings.validate())
        settings.timeout = 5; settings.method = .head; settings.mode = .keepalive
        XCTAssertEqual(try JSONDecoder().decode(PingSettings.self, from: JSONEncoder().encode(settings)), settings)
    }

    func testVlessRealityAndIPv6() throws {
        let profile = try ProfileParser.parseLink("vless://\(uuid)@[2001:db8::1]:443?security=reality&pbk=test-public-key&sid=ab&fp=chrome&flow=xtls-rprx-vision#My%20server")
        XCTAssertEqual(profile.name, "My server")
        XCTAssertEqual(profile.host, "2001:db8::1")
        let data = try ConfigBuilder.build(profile: profile, snapshot: AppSnapshot())
        let config = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        let outbound = try XCTUnwrap((config["outbounds"] as? [[String: Any]])?.first)
        let tls = try XCTUnwrap(outbound["tls"] as? [String: Any])
        XCTAssertEqual((tls["reality"] as? [String: Any])?["public_key"] as? String, "test-public-key")
        XCTAssertEqual(outbound["flow"] as? String, "xtls-rprx-vision")
    }

    func testSubscriptionBase64AndAtomicFailure() throws {
        let text = "vless://\(uuid)@one.example:443#One\ntrojan://secret@two.example:443#Two"
        let profiles = try ProfileParser.parse(Data(text.utf8).base64EncodedString())
        XCTAssertEqual(profiles.count, 2)
        XCTAssertThrowsError(try ProfileParser.parse(text + "\nunsupported://invalid"))
        XCTAssertThrowsError(try ProfileParser.parseLink("vless://bad-uuid@one.example:443"))
        XCTAssertThrowsError(try ProfileParser.parseLink("vless://\(uuid)@one.example:70000"))
        XCTAssertThrowsError(try ProfileParser.parseLink("vless://\(uuid)@one.example:443?type=xhttp"))
        XCTAssertThrowsError(try ProfileParser.parseLink("happ://cryptodata"))
    }

    func testShadowsocksSIP002AndLegacy() throws {
        let auth = Data("aes-128-gcm:secret:with:colons".utf8).base64EncodedString()
        let sip = try ProfileParser.parseLink("ss://\(auth)@example.com:8388#Home")
        XCTAssertEqual(sip.credential, "secret:with:colons")
        let legacy = Data("aes-128-gcm:secret@example.com:8388".utf8).base64EncodedString()
        XCTAssertEqual(try ProfileParser.parseLink("ss://\(legacy)").port, 8388)
        XCTAssertEqual(try ProfileParser.parseLink("ss://\(legacy)").name, "example.com")
        XCTAssertThrowsError(try ProfileParser.parseLink("ss://\(auth)@example.com:8388?plugin=v2ray-plugin"))
    }

    func testSuffixDoesNotMatchSiblingDomain() {
        let rule = RoutingRule(kind: .suffix, value: "example.com", action: .direct)
        XCTAssertTrue(rule.matches("example.com"))
        XCTAssertTrue(rule.matches("api.example.com"))
        XCTAssertFalse(rule.matches("notexample.com"))
        XCTAssertFalse(rule.matches("example.com.attacker.net"))
    }

    func testCIDRBoundaries() {
        XCTAssertTrue(RoutingRule.containsIPv4("192.168.255.255", in: "192.168.0.0/16"))
        XCTAssertFalse(RoutingRule.containsIPv4("192.169.0.0", in: "192.168.0.0/16"))
        XCTAssertTrue(RoutingRule.containsIPv4("255.255.255.255", in: "0.0.0.0/0"))
        XCTAssertTrue(RoutingRule.containsIPv4("1.2.3.4", in: "1.2.3.4/32"))
        XCTAssertFalse(RoutingRule.containsIPv4("1.2.3.5", in: "1.2.3.4/32"))
        XCTAssertFalse(RoutingRule.containsIPv4("999.2.3.4", in: "0.0.0.0/0"))
        XCTAssertThrowsError(try RoutingRule.validate("1.2.3.4/33", kind: .cidr))
    }

    func testRoutingOrderAndDisabledRulesInConfig() throws {
        let profile = try ProfileParser.parseLink("vless://\(uuid)@example.com:443")
        var snapshot = AppSnapshot()
        snapshot.rules = [RoutingRule(kind: .domain, value: "blocked.example", action: .block), RoutingRule(kind: .suffix, value: "direct.example", action: .direct), RoutingRule(kind: .suffix, value: "disabled.example", action: .proxy, enabled: false)]
        let config = try XCTUnwrap(JSONSerialization.jsonObject(with: ConfigBuilder.build(profile: profile, snapshot: snapshot)) as? [String: Any])
        let rules = try XCTUnwrap((config["route"] as? [String: Any])?["rules"] as? [[String: Any]])
        XCTAssertEqual(rules[1]["action"] as? String, "reject")
        XCTAssertEqual(rules[2]["outbound"] as? String, "direct")
        XCTAssertFalse(String(decoding: try ConfigBuilder.build(profile: profile, snapshot: snapshot), as: UTF8.self).contains("disabled.example"))
        snapshot.mode = .global
        XCTAssertFalse(String(decoding: try ConfigBuilder.build(profile: profile, snapshot: snapshot), as: UTF8.self).contains("blocked.example"))
    }

    func testBackupRoundTripAndAuthentication() throws {
        var snapshot = AppSnapshot()
        snapshot.profiles = [try ProfileParser.parseLink("trojan://secret@example.com:443")]
        let password = "a-long-test-password"
        let data = try BackupService.export(snapshot, password: password)
        XCTAssertFalse(String(decoding: data, as: UTF8.self).contains("secret"))
        XCTAssertEqual(try BackupService.restore(data, password: password).profiles.first?.credential, "secret")
        XCTAssertThrowsError(try BackupService.restore(data, password: "wrong-password"))
        XCTAssertThrowsError(try BackupService.export(snapshot, password: "short"))
        var tampered = data; tampered[tampered.count / 2] ^= 1
        XCTAssertThrowsError(try BackupService.restore(tampered, password: password))
    }

    func testExamplesCannotConnect() {
        let example = ProxyProfile(name: "Example", kind: .trojan, host: "example.invalid", port: 443, credential: "sample", isExample: true)
        XCTAssertThrowsError(try ConfigBuilder.build(profile: example, snapshot: AppSnapshot()))
    }

    func testVPNConfigurationHandoffWithoutAppGroup() throws {
        let profile = try ProfileParser.parseLink("trojan://secret@example.com:443")
        let json = String(decoding: try ConfigBuilder.build(profile: profile, snapshot: AppSnapshot()), as: UTF8.self)
        XCTAssertEqual(try TunnelPayload.configuration(options: [TunnelPayload.key: json as NSString], saved: nil), json)
        XCTAssertEqual(try TunnelPayload.configuration(options: nil, saved: [TunnelPayload.key: json]), json)
        XCTAssertThrowsError(try TunnelPayload.configuration(options: nil, saved: nil))
        XCTAssertThrowsError(try TunnelPayload.configuration(options: [TunnelPayload.key: "{}" as NSString], saved: nil))
        let stub = ProxyProfile(name: "В настройках программы", kind: .vless, host: "0.0.0.0", port: 1, credential: uuid)
        XCTAssertThrowsError(try ConfigBuilder.build(profile: stub, snapshot: AppSnapshot()))
    }

    func testHWIDHeadersAreOptInAndRedirectsAreScoped() throws {
        let url = try XCTUnwrap(URL(string: "https://provider.example/sub"))
        let plain = try SubscriptionRequest.make(url: url)
        XCTAssertNil(plain.value(forHTTPHeaderField: "X-HWID"))
        let metadata = DeviceMetadata(hwid: "test-device", os: "iOS", osVersion: "26.0", model: "iPhone", deviceInfo: "AURA; iPhone; iOS 26.0")
        let request = try SubscriptionRequest.make(url: url, metadata: metadata)
        XCTAssertEqual(request.value(forHTTPHeaderField: "X-HWID"), "test-device")
        XCTAssertEqual(request.value(forHTTPHeaderField: "X-Device-OS"), "iOS")
        XCTAssertEqual(SubscriptionRequest.redirect(request, from: url)?.value(forHTTPHeaderField: "X-HWID"), "test-device")
        var other = request; other.url = URL(string: "https://other.example/sub")
        XCTAssertNil(SubscriptionRequest.redirect(other, from: url)?.value(forHTTPHeaderField: "X-HWID"))
        other.url = URL(string: "http://provider.example/sub")
        XCTAssertNil(SubscriptionRequest.redirect(other, from: url))
    }

    func testOldSnapshotsLoadWithHWIDDisabled() throws {
        let data = try JSONEncoder().encode(AppSnapshot())
        var json = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        json.removeValue(forKey: "sendHWID")
        XCTAssertNil(try JSONDecoder().decode(AppSnapshot.self, from: JSONSerialization.data(withJSONObject: json)).sendHWID)
    }

    func testHTTPSCanBeSavedBeforeAnyNetworkCheck() throws {
        let secure = try XCTUnwrap(URL(string: "https://provider.example/sub"))
        XCTAssertNoThrow(try SubscriptionRequest.validateForSaving(url: secure, allowHTTP: false, verifiedURL: nil, profileCount: 0))
        let plain = try XCTUnwrap(URL(string: "http://provider.example/sub"))
        XCTAssertThrowsError(try SubscriptionRequest.validateForSaving(url: plain, allowHTTP: false, verifiedURL: nil, profileCount: 0))
        XCTAssertThrowsError(try SubscriptionRequest.validateForSaving(url: plain, allowHTTP: true, verifiedURL: nil, profileCount: 1))
        XCTAssertThrowsError(try SubscriptionRequest.validateForSaving(url: plain, allowHTTP: true, verifiedURL: secure, profileCount: 1))
        XCTAssertThrowsError(try SubscriptionRequest.validateForSaving(url: plain, allowHTTP: true, verifiedURL: plain, profileCount: 0))
        XCTAssertNoThrow(try SubscriptionRequest.validateForSaving(url: plain, allowHTTP: true, verifiedURL: plain, profileCount: 1))
    }

    func testHTTPNeverReceivesDeviceHeadersAndHTTPSCannotDowngrade() throws {
        let plain = try XCTUnwrap(URL(string: "http://provider.example/sub"))
        let metadata = DeviceMetadata(hwid: "private-device", os: "iOS", osVersion: "26", model: "iPhone", deviceInfo: "AURA")
        XCTAssertThrowsError(try SubscriptionRequest.make(url: plain, metadata: metadata))
        let request = try SubscriptionRequest.make(url: plain, metadata: metadata, allowHTTP: true)
        XCTAssertNil(request.value(forHTTPHeaderField: "X-HWID"))
        XCTAssertNotNil(SubscriptionRequest.redirect(request, from: plain, allowHTTP: true))
        XCTAssertNil(SubscriptionRequest.redirect(request, from: URL(string: "https://provider.example/sub"), allowHTTP: true))
    }

    func testSubscriptionImportsCompatibleLinesWithWarnings() throws {
        let text = "vless://\(uuid)@example.com:443?type=raw&security=reality&pbk=key&spx=%2F&headerType=none#Reality\nvless://\(uuid)@other.example:443?type=xhttp#XHTTP"
        let result = try SubscriptionDocument.parse(Data(text.utf8).base64EncodedString())
        XCTAssertEqual(result.profiles.count, 1)
        XCTAssertEqual(result.profiles[0].query["type"], "tcp")
        XCTAssertEqual(result.skippedCount, 1)
        XCTAssertTrue(result.warnings[0].contains("xhttp"))
        XCTAssertThrowsError(try SubscriptionDocument.parse("<!doctype html><html>Provider page</html>"))
    }

    func testXrayAndSingBoxJSONSubscriptions() throws {
        let xray: [String: Any] = ["remarks": "Xray JSON", "outbounds": [["protocol": "vless", "settings": ["vnext": [["address": "example.com", "port": 443, "users": [["id": uuid, "flow": "xtls-rprx-vision"]]]]], "streamSettings": ["network": "raw", "security": "reality", "realitySettings": ["publicKey": "test-key", "shortId": "ab", "serverName": "example.com", "fingerprint": "chrome"]]]]]
        let xrayResult = try SubscriptionDocument.parse(String(decoding: JSONSerialization.data(withJSONObject: [xray]), as: UTF8.self))
        XCTAssertEqual(xrayResult.profiles[0].name, "Xray JSON")
        XCTAssertEqual(xrayResult.profiles[0].query["pbk"], "test-key")
        let singBox = "{\"outbounds\":[{\"type\":\"trojan\",\"tag\":\"JSON server\",\"server\":\"example.com\",\"server_port\":443,\"password\":\"secret\",\"tls\":{\"enabled\":true,\"server_name\":\"example.com\"}}]}"
        let singResult = try SubscriptionDocument.parse(singBox)
        XCTAssertEqual(singResult.profiles[0].kind, .trojan)
        XCTAssertEqual(singResult.profiles[0].credential, "secret")
        XCTAssertNoThrow(try ConfigBuilder.build(profile: singResult.profiles[0], snapshot: AppSnapshot()))
    }
}
