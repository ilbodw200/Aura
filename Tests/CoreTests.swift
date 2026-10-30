import XCTest
@testable import Aura

final class CoreTests: XCTestCase {
    private let uuid = "11111111-2222-4333-8444-555555555555"

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
}
