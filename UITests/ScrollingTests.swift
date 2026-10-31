import XCTest

final class ScrollingTests: XCTestCase {
    func testLongSubscriptionScrollsSelectsLastServerAndReturnsToTop() {
        let app = XCUIApplication()
        app.launchArguments = ["--ui-scroll-fixture"]
        app.launch()
        let toggle = app.buttons["dashboard.connection.toggle"]
        XCTAssertTrue(toggle.waitForExistence(timeout: 20))
        let list = app.collectionViews.firstMatch.exists ? app.collectionViews.firstMatch : app.tables.firstMatch
        XCTAssertTrue(list.waitForExistence(timeout: 10))
        let last = app.buttons["server.select.00000000-0000-4000-8000-000000000057"]
        for _ in 0..<24 {
            // A partially exposed row can be reported as hittable while its
            // centre is still covered by the persistent connection dock.
            if last.exists && last.isHittable && last.frame.maxY < toggle.frame.minY { break }
            list.swipeUp()
        }
        XCTAssertTrue(last.exists && last.isHittable, "The final server must be reachable by swiping the list")
        XCTAssertLessThan(last.frame.maxY, toggle.frame.minY, "The full server button must be above the connection dock")
        XCTAssertTrue(toggle.isHittable, "Connection remains accessible while scrolling")
        XCTAssertTrue(app.buttons["dashboard.add"].isHittable)
        last.tap()
        XCTAssertEqual(last.value as? String, "Выбран")
        let bottom = XCTAttachment(screenshot: app.screenshot())
        bottom.name = "Last server and persistent connection controls"
        bottom.lifetime = .keepAlways
        add(bottom)
        app.buttons["dashboard.scrollToTop"].tap()
        let power = app.buttons["Подключить VPN"]
        XCTAssertTrue(power.waitForExistence(timeout: 5))
        XCTAssertTrue(power.isHittable, "Return to the main power control must work")
        let top = XCTAttachment(screenshot: app.screenshot())
        top.name = "Top of list after return"
        top.lifetime = .keepAlways
        add(top)
    }
}
