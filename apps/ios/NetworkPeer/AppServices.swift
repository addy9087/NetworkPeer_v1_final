import Foundation
import SwiftUI
import UIKit
import UserNotifications

#if canImport(FirebaseCore)
import FirebaseCore
#endif

#if canImport(FirebaseMessaging)
import FirebaseMessaging
#endif

#if canImport(SocketIO)
import SocketIO
#endif

#if canImport(StripePaymentSheet)
import StripePaymentSheet
#endif

struct AppConfiguration {
    let apiBaseURL: URL?
    let apiConfigurationError: String?
    let stripePublishableKey: String?
    let socketIOEnabled: Bool
    let maximumEvidenceBytes: Int64

    init(bundle: Bundle = .main, processInfo: ProcessInfo = .processInfo) {
        let isUITest = processInfo.arguments.contains("-ui-testing")
        let testURL = isUITest ? processInfo.environment["NETWORKPEER_UI_TEST_API_BASE_URL"] : nil
        let rawAPIURL = testURL ?? bundle.object(forInfoDictionaryKey: "NETWORKPEER_API_BASE_URL") as? String
        let rawStripeKey = bundle.object(forInfoDictionaryKey: "NETWORKPEER_STRIPE_PUBLISHABLE_KEY") as? String
        let rawSocketFlag = bundle.object(forInfoDictionaryKey: "NETWORKPEER_SOCKET_IO_ENABLED") as? String
        let rawEvidenceLimit = bundle.object(forInfoDictionaryKey: "NETWORKPEER_MAX_EVIDENCE_BYTES") as? String

        if let rawAPIURL, Self.isConfiguredAPIURL(rawAPIURL), let url = URL(string: rawAPIURL) {
            apiBaseURL = url
            apiConfigurationError = nil
        } else {
            apiBaseURL = nil
            apiConfigurationError = "Set NETWORKPEER_API_BASE_URL in the matching ignored Debug or Release local xcconfig before signing in."
        }
        stripePublishableKey = (Self.isConfiguredValue(rawStripeKey) && rawStripeKey?.hasPrefix("pk_") == true) ? rawStripeKey : nil
        socketIOEnabled = rawSocketFlag?.uppercased() == "YES"
        if let rawEvidenceLimit,
           Self.isConfiguredValue(rawEvidenceLimit),
           let value = Int64(rawEvidenceLimit.trimmingCharacters(in: .whitespacesAndNewlines)),
           EvidenceUploadLimits.isValid(value) {
            maximumEvidenceBytes = value
        } else {
            maximumEvidenceBytes = EvidenceUploadLimits.defaultMaximumFileSizeBytes
        }
    }

    private static func isConfiguredValue(_ value: String?) -> Bool {
        guard let value = value?.trimmingCharacters(in: .whitespacesAndNewlines) else { return false }
        return !value.isEmpty && !value.contains("$(")
    }

    private static func isConfiguredAPIURL(_ value: String) -> Bool {
        guard isConfiguredValue(value), let host = URL(string: value)?.host?.lowercased() else {
            return false
        }
        let isPlaceholderHost = host == "example" || host.hasSuffix(".example") ||
            host == "example.com" || host.hasSuffix(".example.com") ||
            host == "invalid" || host.hasSuffix(".invalid")
        return !isPlaceholderHost
    }
}

enum StripePaymentOutcome: Equatable {
    case completed
    case cancelled
    case failed(String)
    case unavailable(String)
}

@MainActor
final class StripePaymentCoordinator: ObservableObject {
    @Published private(set) var configurationMessage: String?

    private let publishableKey: String?

    #if canImport(StripePaymentSheet)
    private var paymentSheet: PaymentSheet?
    #endif

    init(publishableKey: String?) {
        self.publishableKey = publishableKey
        #if canImport(StripePaymentSheet)
        if let publishableKey {
            StripeAPI.defaultPublishableKey = publishableKey
        } else {
            configurationMessage = "Card payments are unavailable until a public Stripe publishable key is configured."
        }
        #else
        configurationMessage = "Card payments are unavailable because StripePaymentSheet is not linked to this build."
        #endif
    }

    func present(clientSecret: String) async -> StripePaymentOutcome {
        guard !clientSecret.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return .unavailable("The server did not provide a payment intent client secret.")
        }
        guard publishableKey != nil else {
            return .unavailable("Card payments require a public Stripe publishable key in the matching local xcconfig.")
        }

        #if canImport(StripePaymentSheet)
        guard let presenter = activePresenter() else {
            return .unavailable("A payment screen could not be presented right now. Try again from the job detail.")
        }
        var configuration = PaymentSheet.Configuration()
        configuration.merchantDisplayName = "NetworkPeer"
        paymentSheet = PaymentSheet(paymentIntentClientSecret: clientSecret, configuration: configuration)
        guard let paymentSheet else {
            return .unavailable("The payment sheet could not be prepared.")
        }
        return await withCheckedContinuation { continuation in
            paymentSheet.present(from: presenter) { [weak self] result in
                let outcome: StripePaymentOutcome
                switch result {
                case .completed:
                    outcome = .completed
                case .canceled:
                    outcome = .cancelled
                case let .failed(error):
                    outcome = .failed(error.localizedDescription)
                }
                Task { @MainActor in
                    self?.paymentSheet = nil
                    continuation.resume(returning: outcome)
                }
            }
        }
        #else
        return .unavailable("StripePaymentSheet is not linked to this build.")
        #endif
    }

    #if canImport(StripePaymentSheet)
    private func activePresenter() -> UIViewController? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let root = scenes
            .flatMap(\.windows)
            .first(where: { $0.isKeyWindow })?
            .rootViewController
        return topViewController(from: root)
    }

    private func topViewController(from controller: UIViewController?) -> UIViewController? {
        guard let controller else { return nil }
        if let presented = controller.presentedViewController {
            return topViewController(from: presented)
        }
        if let navigation = controller as? UINavigationController {
            return topViewController(from: navigation.visibleViewController)
        }
        if let tab = controller as? UITabBarController {
            return topViewController(from: tab.selectedViewController)
        }
        return controller
    }
    #endif
}

@MainActor
final class RealtimeCoordinator {
    enum State: Equatable {
        case disabled
        case connecting
        case connected
        case unavailable
    }

    @Published private(set) var state: State = .disabled

    private let baseURL: URL?
    private let enabled: Bool
    private var synchronize: (@MainActor () async -> Void)?

    #if canImport(SocketIO)
    private var manager: SocketManager?
    private var socket: SocketIOClient?
    #endif

    init(baseURL: URL?, enabled: Bool) {
        self.baseURL = baseURL
        self.enabled = enabled
    }

    func start(accessToken: String, synchronize: @escaping @MainActor () async -> Void) {
        self.synchronize = synchronize
        guard enabled, let baseURL else {
            state = .disabled
            return
        }

        #if canImport(SocketIO)
        guard let origin = realtimeOrigin(from: baseURL) else {
            state = .unavailable
            return
        }
        stop()
        state = .connecting
        let manager = SocketManager(
            socketURL: origin,
            config: [
                .log(false),
                .compress,
                .forceWebsockets(true),
                .path("/api/v1/realtime"),
                .reconnects(true),
                .reconnectAttempts(-1),
            ],
        )
        let socket = manager.defaultSocket
        socket.on(clientEvent: .connect) { [weak self] _, _ in
            Task { @MainActor in
                self?.state = .connected
                self?.requestReconciliation()
            }
        }
        socket.on(clientEvent: .reconnect) { [weak self] _, _ in
            Task { @MainActor in
                self?.state = .connecting
                self?.requestReconciliation()
            }
        }
        socket.on("sync:ready") { [weak self] _, _ in
            Task { @MainActor in self?.requestReconciliation() }
        }
        socket.on("sync:event") { [weak self] _, _ in
            Task { @MainActor in self?.requestReconciliation() }
        }
        self.manager = manager
        self.socket = socket
        // Socket.IO auth is sent in the namespace CONNECT payload, not as a URL query token.
        socket.connect(withPayload: ["token": accessToken])
        #else
        state = .unavailable
        #endif
    }

    func stop() {
        #if canImport(SocketIO)
        socket?.removeAllHandlers()
        socket?.disconnect()
        socket = nil
        manager = nil
        #endif
        if enabled { state = .disabled }
    }

    func requestReconciliation() {
        guard let synchronize else { return }
        Task { @MainActor in await synchronize() }
    }

    #if canImport(SocketIO)
    private func realtimeOrigin(from apiURL: URL) -> URL? {
        guard var components = URLComponents(url: apiURL, resolvingAgainstBaseURL: false) else { return nil }
        components.path = ""
        components.query = nil
        components.fragment = nil
        return components.url
    }
    #endif
}

enum PushPermissionState: Equatable {
    case unknown
    case denied
    case authorized
    case provisional

    var description: String {
        switch self {
        case .unknown: "Notifications are not configured."
        case .denied: "Notifications are disabled. Enable them in Settings to receive job updates."
        case .authorized: "Notifications are enabled."
        case .provisional: "Quiet notifications are enabled."
        }
    }
}

@MainActor
final class PushPermissionManager: ObservableObject {
    @Published private(set) var state: PushPermissionState = .unknown

    func refresh() async {
        let settings = await UNUserNotificationCenter.current().notificationSettings()
        state = Self.state(for: settings.authorizationStatus)
    }

    func requestPermission() async {
        let center = UNUserNotificationCenter.current()
        let settings = await center.notificationSettings()
        if settings.authorizationStatus == .notDetermined {
            _ = try? await center.requestAuthorization(options: [.alert, .badge, .sound])
        }
        await refresh()
        if state == .authorized || state == .provisional {
            UIApplication.shared.registerForRemoteNotifications()
        }
    }

    private static func state(for status: UNAuthorizationStatus) -> PushPermissionState {
        switch status {
        case .authorized, .ephemeral: .authorized
        case .provisional: .provisional
        case .denied: .denied
        case .notDetermined: .unknown
        @unknown default: .unknown
        }
    }
}

actor PushRegistrationStore {
    static let shared = PushRegistrationStore()

    private struct RegistrationFlight {
        let id: UUID
        let token: String
        let api: NetworkPeerAPI
        let task: Task<Void, Never>
    }

    private var token: String?
    private var api: NetworkPeerAPI?
    private var registrationFlight: RegistrationFlight?

    func update(token: String) async {
        self.token = token
        await registerCurrentToken()
    }

    func register(using api: NetworkPeerAPI?) async {
        self.api = api
        await registerCurrentToken()
    }

    func unregister(using api: NetworkPeerAPI?, retryAfterRefresh: Bool = true) async {
        let unregisterAPI = api ?? self.api
        let currentToken = token
        // Do not let a late Firebase token callback re-register while logout is in progress.
        self.api = nil

        if let registrationFlight {
            await registrationFlight.task.value
            if self.registrationFlight?.id == registrationFlight.id {
                self.registrationFlight = nil
            }
        }

        guard let currentToken, let unregisterAPI else { return }
        _ = try? await unregisterAPI.unregisterDevice(token: currentToken, retryAfterRefresh: retryAfterRefresh)
    }

    private func registerCurrentToken() async {
        while let token, let api {
            if let registrationFlight {
                await registrationFlight.task.value
                if self.registrationFlight?.id == registrationFlight.id {
                    self.registrationFlight = nil
                }
                if self.token == registrationFlight.token, self.api === registrationFlight.api {
                    return
                }
                continue
            }

            let id = UUID()
            let task = Task { _ = try? await api.registerDevice(token: token) }
            registrationFlight = RegistrationFlight(id: id, token: token, api: api, task: task)
            await task.value
            if registrationFlight?.id == id {
                registrationFlight = nil
            }
            return
        }
    }
}

@MainActor
final class PushNotificationCoordinator {
    static let shared = PushNotificationCoordinator()

    private static let localNotificationMarkerKey = "networkpeer_local_data_push"
    private static let localNotificationMarkerValue = "1"

    private let sessionStore = KeychainSessionStore()
    private let center = UNUserNotificationCenter.current()
    private var activeAccountID: String?
    private var isDeliveryEnabled = true
    private var notificationGeneration = 0

    func activate(for accountID: String) async {
        let shouldInvalidate = !isDeliveryEnabled || (activeAccountID != nil && activeAccountID != accountID)
        if shouldInvalidate { notificationGeneration &+= 1 }
        activeAccountID = accountID
        isDeliveryEnabled = true
        await removeNotifications(notFor: accountID)
    }

    func deactivate() {
        notificationGeneration &+= 1
        activeAccountID = nil
        isDeliveryEnabled = false
        center.removeAllPendingNotificationRequests()
        center.removeAllDeliveredNotifications()
    }

    func handleRemoteNotification(_ userInfo: [AnyHashable: Any]) async -> UIBackgroundFetchResult {
        guard let payload = DataOnlyPushPayload(userInfo: userInfo), accepts(payload) else {
            return .noData
        }

        let didSchedule = await schedule(payload)
        guard accepts(payload) else { return .noData }
        // Do not paginate sync in an APNs background callback. The one local
        // notification is bounded; foreground activation performs full sync.
        return didSchedule ? .newData : .noData
    }

    func presentationOptions(for userInfo: [AnyHashable: Any]) -> UNNotificationPresentationOptions {
        guard isLocalNotification(userInfo),
              let payload = DataOnlyPushPayload(userInfo: userInfo),
              accepts(payload) else {
            return []
        }
        return [.banner, .badge, .sound]
    }

    func routeNotificationResponse(_ userInfo: [AnyHashable: Any]) {
        guard isLocalNotification(userInfo),
              let payload = DataOnlyPushPayload(userInfo: userInfo),
              accepts(payload) else {
            return
        }
        DeepLinkRouter.shared.route(pushPayload: payload)
    }

    private func accepts(_ payload: DataOnlyPushPayload) -> Bool {
        guard isDeliveryEnabled,
              activeAccountID == nil || activeAccountID == payload.recipientUserID else {
            return false
        }
        return payload.isForActiveSession(sessionStore.read())
    }

    private func schedule(_ payload: DataOnlyPushPayload) async -> Bool {
        guard accepts(payload) else { return false }
        let generation = notificationGeneration
        var userInfo = payload.userInfo
        userInfo[AnyHashable(Self.localNotificationMarkerKey)] = Self.localNotificationMarkerValue

        let content = UNMutableNotificationContent()
        content.title = payload.title
        content.body = payload.body
        content.sound = .default
        content.userInfo = userInfo
        let identifier = "networkpeer.push.\(payload.recipientUserID).\(payload.cursor)"
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 1, repeats: false)
        let request = UNNotificationRequest(identifier: identifier, content: content, trigger: trigger)

        do {
            try await center.add(request)
        } catch {
            return false
        }

        guard generation == notificationGeneration, accepts(payload) else {
            center.removePendingNotificationRequests(withIdentifiers: [identifier])
            center.removeDeliveredNotifications(withIdentifiers: [identifier])
            return false
        }
        return true
    }

    private func isLocalNotification(_ userInfo: [AnyHashable: Any]) -> Bool {
        userInfo[AnyHashable(Self.localNotificationMarkerKey)] as? String == Self.localNotificationMarkerValue
    }

    private func removeNotifications(notFor accountID: String) async {
        let pending = await center.pendingNotificationRequests()
        let pendingIDs = pending
            .filter { ($0.content.userInfo[AnyHashable("recipient_user_id")] as? String) != accountID }
            .map(\.identifier)
        center.removePendingNotificationRequests(withIdentifiers: pendingIDs)

        let delivered = await center.deliveredNotifications()
        let deliveredIDs = delivered
            .filter { ($0.request.content.userInfo[AnyHashable("recipient_user_id")] as? String) != accountID }
            .map(\.request.identifier)
        center.removeDeliveredNotifications(withIdentifiers: deliveredIDs)
    }
}

enum DeepLinkDestination: Equatable {
    case job(String)
    case inbox
}

@MainActor
final class DeepLinkRouter: ObservableObject {
    static let shared = DeepLinkRouter()

    @Published private(set) var destination: DeepLinkDestination?

    func route(url: URL) {
        let pathParts = url.pathComponents.filter { $0 != "/" }
        let queryJobID = URLComponents(url: url, resolvingAgainstBaseURL: false)?
            .queryItems?
            .first(where: { $0.name == "job_id" })?
            .value

        if url.host == "inbox" || pathParts.first == "inbox" {
            destination = .inbox
        } else if url.host == "job", let id = pathParts.first, !id.isEmpty {
            destination = .job(id)
        } else if pathParts.count >= 2, pathParts[pathParts.count - 2] == "job" {
            destination = .job(pathParts[pathParts.count - 1])
        } else if let queryJobID, !queryJobID.isEmpty {
            destination = .job(queryJobID)
        }
    }

    func route(pushPayload: DataOnlyPushPayload) {
        if let jobID = pushPayload.jobID {
            destination = .job(jobID)
        } else {
            destination = .inbox
        }
    }

    func clear() {
        destination = nil
    }

    func open(_ destination: DeepLinkDestination) {
        self.destination = destination
    }
}

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(
        _: UIApplication,
        didFinishLaunchingWithOptions _: [UIApplication.LaunchOptionsKey: Any]? = nil,
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        #if canImport(FirebaseCore) && canImport(FirebaseMessaging)
        if Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil,
           FirebaseApp.app() == nil {
            FirebaseApp.configure()
            Messaging.messaging().delegate = self
        }
        #endif
        return true
    }

    func application(_: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        #if canImport(FirebaseMessaging) && canImport(FirebaseCore)
        if FirebaseApp.app() != nil {
            // The API accepts Firebase registration tokens, not raw APNs device tokens.
            Messaging.messaging().apnsToken = deviceToken
        }
        #endif
    }

    func application(_: UIApplication, didFailToRegisterForRemoteNotificationsWithError _: Error) {
        // Permission state remains visible in-app; registration can be retried after the next launch.
    }

    func application(
        _: UIApplication,
        didReceiveRemoteNotification userInfo: [AnyHashable: Any],
        fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void,
    ) {
        Task { @MainActor in
            completionHandler(await PushNotificationCoordinator.shared.handleRemoteNotification(userInfo))
        }
    }

    func userNotificationCenter(
        _: UNUserNotificationCenter,
        willPresent notification: UNNotification,
    ) async -> UNNotificationPresentationOptions {
        await PushNotificationCoordinator.shared.presentationOptions(for: notification.request.content.userInfo)
    }

    func userNotificationCenter(
        _: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
    ) async {
        await PushNotificationCoordinator.shared.routeNotificationResponse(response.notification.request.content.userInfo)
    }
}

#if canImport(FirebaseMessaging)
extension AppDelegate: MessagingDelegate {
    func messaging(_: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let fcmToken, !fcmToken.isEmpty else { return }
        Task { await PushRegistrationStore.shared.update(token: fcmToken) }
    }
}
#endif
