import XCTest

final class NetworkPeerUITests: XCTestCase {
    func testConfiguredBuildShowsSecureLogin() {
        let app = XCUIApplication()
        app.launchArguments = ["-ui-testing"]
        app.launchEnvironment["NETWORKPEER_UI_TEST_API_BASE_URL"] = "https://api.test.invalid/api/v1/"
        app.launch()

        XCTAssertTrue(app.staticTexts["Secure sign in"].waitForExistence(timeout: 3))
        XCTAssertTrue(app.textFields["login.phone"].exists)
        XCTAssertTrue(app.buttons["login.submit"].exists)
    }
}
