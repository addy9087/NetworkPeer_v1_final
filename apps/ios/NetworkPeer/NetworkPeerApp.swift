import CoreLocation
import SwiftUI
import UIKit

@main
struct NetworkPeerApp: App {
    @StateObject private var model = AppModel()
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(model)
                .tint(NetworkPeerTheme.indigo)
                .onOpenURL { model.deepLinks.route(url: $0) }
                .onChange(of: scenePhase) { _, phase in
                    if phase == .active {
                        Task { await model.applicationBecameActive() }
                    }
                }
                .task { await model.bootstrap() }
        }
    }
}

@MainActor
final class AppModel: ObservableObject {
    @Published private(set) var session: StoredSession?
    @Published private(set) var configurationError: String?
    @Published private(set) var syncError: String?
    @Published private(set) var isSynchronizing = false
    @Published private(set) var syncGeneration = 0

    let sessionStore: KeychainSessionStore
    let api: NetworkPeerAPI?
    let evidenceUploader: EvidenceUploader?
    let localStore: LocalStore
    let paymentCoordinator: StripePaymentCoordinator
    let notificationPermissions: PushPermissionManager
    let realtimeCoordinator: RealtimeCoordinator
    let deepLinks = DeepLinkRouter.shared

    private var hasBootstrapped = false
    private var synchronizeAgain = false
    private var realtimeAccessToken: String?
    private var signingOutSession: StoredSession?

    init() {
        sessionStore = KeychainSessionStore()
        session = sessionStore.read()
        localStore = LocalStore.make()
        let configuration = AppConfiguration()
        paymentCoordinator = StripePaymentCoordinator(publishableKey: configuration.stripePublishableKey)
        notificationPermissions = PushPermissionManager()
        realtimeCoordinator = RealtimeCoordinator(baseURL: configuration.apiBaseURL, enabled: configuration.socketIOEnabled)
        guard let url = configuration.apiBaseURL else {
            configurationError = configuration.apiConfigurationError
            api = nil
            evidenceUploader = nil
            return
        }
        do {
            let client = try NetworkPeerAPI(
                baseURL: url,
                sessionStore: sessionStore,
                maximumEvidenceBytes: configuration.maximumEvidenceBytes,
            )
            api = client
            evidenceUploader = EvidenceUploader(api: client, maximumEvidenceBytes: configuration.maximumEvidenceBytes)
        } catch {
            configurationError = error.localizedDescription
            api = nil
            evidenceUploader = nil
        }
    }

    func signedIn(_ nextSession: StoredSession) {
        session = nextSession
        Task { await startSignedInServices(for: nextSession) }
    }

    func signOut() async {
        guard let sessionToSignOut = session else { return }
        signingOutSession = sessionToSignOut
        defer {
            if signingOutSession == sessionToSignOut {
                signingOutSession = nil
            }
        }
        realtimeCoordinator.stop()
        realtimeAccessToken = nil
        PushNotificationCoordinator.shared.deactivate()
        let currentAPI = api
        // Device cleanup is best effort; it must not postpone refresh-family
        // revocation when the access token has already expired.
        Task { await PushRegistrationStore.shared.unregister(using: currentAPI, retryAfterRefresh: false) }
        await currentAPI?.logout()
        let didClear = (try? sessionStore.clear(ifMatches: sessionToSignOut)) ?? false
        guard session == sessionToSignOut else { return }
        if !didClear, let currentSession = sessionStore.read() {
            session = currentSession
            signingOutSession = nil
            await startSignedInServices(for: currentSession)
            return
        }
        session = nil
        syncError = nil
        deepLinks.clear()
    }

    func bootstrap() async {
        guard !hasBootstrapped else { return }
        hasBootstrapped = true
        await notificationPermissions.refresh()
        if let session {
            await startSignedInServices(for: session)
        }
    }

    func applicationBecameActive() async {
        await notificationPermissions.refresh()
        guard let session, signingOutSession == nil else { return }
        await PushRegistrationStore.shared.register(using: api)
        realtimeCoordinator.requestReconciliation()
        await synchronize()
        if (realtimeCoordinator.state == .disabled || realtimeCoordinator.state == .unavailable), let currentSession = self.session {
            startRealtime(for: currentSession)
        }
    }

    func synchronize() async {
        guard let api, let session, signingOutSession == nil else { return }
        guard !isSynchronizing else {
            synchronizeAgain = true
            return
        }
        isSynchronizing = true
        syncError = nil
        defer {
            isSynchronizing = false
            if synchronizeAgain {
                synchronizeAgain = false
                Task { await synchronize() }
            }
        }

        var receivedEvents = false
        do {
            receivedEvents = try await synchronizeAllPages(using: api, accountID: session.user.id, role: session.user.role)
        } catch {
            if !isRecoverableCursorError(error) {
                syncError = error.localizedDescription
            } else {
                do {
                    localStore.resetSyncedData(accountID: session.user.id)
                    try await reconcileServerState(using: api, session: session)
                    receivedEvents = try await synchronizeAllPages(using: api, accountID: session.user.id, role: session.user.role)
                } catch {
                    syncError = error.localizedDescription
                }
            }
        }
        if receivedEvents {
            do {
                try await reconcileServerState(using: api, session: session)
            } catch {
                syncError = "Updates were received, but the latest server snapshot could not be loaded: \(error.localizedDescription)"
            }
        }
        if let refreshedSession = await api.session(), refreshedSession.accessToken != session.accessToken {
            self.session = refreshedSession
            startRealtime(for: refreshedSession, force: true)
        }
    }

    func cached<Value: Decodable>(_ type: Value.Type, name: String) -> Value? {
        guard let session else { return nil }
        return localStore.cached(type, key: localStore.cacheKey(accountID: session.user.id, name: name))
    }

    func cache<Value: Encodable>(_ value: Value, name: String) {
        guard let session else { return }
        localStore.cache(value, key: localStore.cacheKey(accountID: session.user.id, name: name))
    }

    func confirmedEvidence(jobID: String) -> [String: EvidenceSummary] {
        cached([String: EvidenceSummary].self, name: "evidence.\(jobID)") ?? [:]
    }

    func pendingEvidence(jobID: String) -> [PendingEvidence] {
        guard let session else { return [] }
        return localStore.pendingEvidence(accountID: session.user.id, jobID: jobID)
    }

    func pendingClientJob() -> CreateJobRequest? {
        guard let session else { return nil }
        return localStore.pendingClientJob(accountID: session.user.id)
    }

    func savePendingClientJob(_ request: CreateJobRequest) {
        guard let session else { return }
        localStore.savePendingClientJob(request, accountID: session.user.id)
    }

    func discardPendingClientJob() {
        guard let session else { return }
        localStore.discardPendingClientJob(accountID: session.user.id)
    }

    var inboxItems: [InboxItem] {
        _ = syncGeneration
        guard let session else { return [] }
        return localStore.inbox(accountID: session.user.id)
    }

    func markInboxRead(id: String) {
        localStore.markInboxRead(id: id)
        syncGeneration += 1
    }

    func markAllInboxRead() {
        guard let session else { return }
        localStore.markAllInboxRead(accountID: session.user.id)
        syncGeneration += 1
    }

    func loadInbox(beforeCursor: String? = nil, limit: Int = 50) async throws -> NotificationPage {
        guard let api, let session else {
            throw NetworkPeerAPIError.invalidConfiguration("Inbox is unavailable until the API is configured.")
        }
        let page = try await api.notifications(beforeCursor: beforeCursor, limit: limit)
        localStore.applyNotifications(page, accountID: session.user.id, replacing: beforeCursor == nil)
        syncGeneration += 1
        return page
    }

    func markInboxReadRemotely(_ item: InboxItem) async throws {
        // Keep the local read state useful while offline, then reconcile it with the API.
        markInboxRead(id: item.id)
        guard let api, let session else { return }
        let notification = try await api.markNotificationRead(id: item.eventID)
        localStore.applyNotification(notification, accountID: session.user.id)
        syncGeneration += 1
    }

    @discardableResult
    func markAllInboxReadRemotely() async throws -> Int {
        markAllInboxRead()
        guard let api else { return 0 }
        let result = try await api.markAllNotificationsRead()
        return result.markedCount
    }

    func enqueueAndUploadEvidence(
        jobID: String,
        subtaskID: String,
        sourceURL: URL,
        location: Point? = nil,
    ) async throws -> EvidenceSummary {
        guard let session else {
            throw NetworkPeerAPIError.server(code: "UNAUTHENTICATED", message: "Sign in again to upload evidence.", statusCode: 401)
        }
        let capturedAt = ISO8601DateFormatter().string(from: .now)
        let pending = try localStore.enqueueEvidence(
            accountID: session.user.id,
            jobID: jobID,
            subtaskID: subtaskID,
            sourceURL: sourceURL,
            capturedAtISO8601: capturedAt,
            location: location,
            idempotencyKey: UUID().uuidString,
        )
        return try await upload(pending)
    }

    func retryPendingEvidence(jobID: String) async -> [EvidenceSummary] {
        var uploaded: [EvidenceSummary] = []
        for pending in pendingEvidence(jobID: jobID) {
            if let evidence = try? await upload(pending) {
                uploaded.append(evidence)
            }
        }
        return uploaded
    }

    private func startSignedInServices(for session: StoredSession) async {
        guard self.session == session, signingOutSession == nil else { return }
        await PushNotificationCoordinator.shared.activate(for: session.user.id)
        guard self.session == session, signingOutSession == nil else { return }
        await PushRegistrationStore.shared.register(using: api)
        guard self.session == session, signingOutSession == nil else { return }
        await synchronize()
        startRealtime(for: self.session ?? session)
    }

    private func startRealtime(for session: StoredSession, force: Bool = false) {
        guard force || realtimeAccessToken != session.accessToken else { return }
        realtimeAccessToken = session.accessToken
        realtimeCoordinator.start(accessToken: session.accessToken) { [weak self] in
            guard let self else { return }
            await self.synchronize()
        }
    }

    private func synchronizeAllPages(
        using api: NetworkPeerAPI,
        accountID: String,
        role: UserRole,
    ) async throws -> Bool {
        if role == .worker {
            return try await synchronizeWorkerPages(using: api, accountID: accountID)
        }
        var cursor = localStore.syncCursor(accountID: accountID)
        var pageCount = 0
        var receivedEvents = false
        while true {
            pageCount += 1
            guard pageCount <= 1_000 else {
                throw NetworkPeerAPIError.invalidResponse
            }
            let page = try await api.sync(cursor: cursor)
            guard !page.hasMore || page.nextCursor != cursor else {
                throw NetworkPeerAPIError.invalidResponse
            }
            localStore.applySyncPage(page, accountID: accountID)
            receivedEvents = receivedEvents || !page.events.isEmpty
            if !page.events.isEmpty { syncGeneration += 1 }
            if !page.hasMore { return receivedEvents }
            cursor = page.nextCursor
        }
    }

    private func synchronizeWorkerPages(using api: NetworkPeerAPI, accountID: String) async throws -> Bool {
        var cursor = localStore.workerSyncCursor(accountID: accountID)
        var pageCount = 0
        var receivedEvents = false
        while true {
            pageCount += 1
            guard pageCount <= 1_000 else {
                throw NetworkPeerAPIError.invalidResponse
            }
            let page = try await api.workerSync(cursor: cursor)
            guard !page.hasMore || page.nextCursor != cursor else {
                throw NetworkPeerAPIError.invalidResponse
            }
            localStore.applyWorkerSyncPage(page, accountID: accountID)
            cacheWorkerSyncSnapshot(page)
            let pageChanged = !page.events.isEmpty || !page.jobs.isEmpty || !page.snapshotJobs.isEmpty || !page.ledgerEntries.isEmpty || !page.removedJobIDs.isEmpty
            receivedEvents = receivedEvents || pageChanged
            if pageChanged { syncGeneration += 1 }
            if !page.hasMore { return receivedEvents }
            cursor = page.nextCursor
        }
    }

    private func cacheWorkerSyncSnapshot(_ page: WorkerSyncPage) {
        var jobs = page.snapshotJobs.isEmpty
            ? cached([WorkerJobDetail].self, name: "worker.assigned.jobs") ?? []
            : page.snapshotJobs
        var jobsByID: [String: WorkerJobDetail] = [:]
        for job in jobs {
            jobsByID[job.id] = job
        }
        for job in page.jobs {
            jobsByID[job.id] = job
        }
        for id in page.removedJobIDs {
            jobsByID[id] = nil
        }
        jobs = jobsByID.values.sorted { $0.updatedAt > $1.updatedAt }
        cache(jobs, name: "worker.assigned.jobs")

        guard !page.ledgerEntries.isEmpty else { return }
        var entriesByID: [String: WalletLedgerEntry] = [:]
        for entry in cached([WalletLedgerEntry].self, name: "worker.ledger") ?? [] {
            entriesByID[entry.id] = entry
        }
        for entry in page.ledgerEntries {
            entriesByID[entry.id] = entry
        }
        cache(entriesByID.values.sorted { $0.createdAt > $1.createdAt }, name: "worker.ledger")
    }

    private func reconcileServerState(using api: NetworkPeerAPI, session: StoredSession) async throws {
        switch session.user.role {
        case .client:
            async let jobs = api.clientJobs()
            async let wallet = api.clientWallet()
            cache(try await jobs, name: "client.jobs")
            cache(try await wallet, name: "client.wallet")
        case .worker:
            cache(try await api.workerWallet(), name: "worker.wallet")
        case .admin:
            break
        }
    }

    private func isRecoverableCursorError(_ error: Error) -> Bool {
        guard case let NetworkPeerAPIError.server(code, _, statusCode) = error else { return false }
        return statusCode == 409 || ["CURSOR_EXPIRED", "INVALID_CURSOR", "SYNC_CURSOR_INVALID"].contains(code)
    }

    private func upload(_ pending: PendingEvidence) async throws -> EvidenceSummary {
        guard let uploader = evidenceUploader else {
            throw NetworkPeerAPIError.invalidConfiguration("Evidence uploads are unavailable until the API is configured.")
        }
        guard FileManager.default.fileExists(atPath: pending.localFileURL.path) else {
            let error = NetworkPeerAPIError.unreadableEvidence
            localStore.markEvidenceFailure(id: pending.id, error: error)
            throw error
        }
        do {
            let result = try await uploader.upload(
                jobID: pending.jobID,
                subtaskID: pending.subtaskID,
                fileURL: pending.localFileURL,
                capturedAtISO8601: pending.capturedAtISO8601,
                location: pending.location,
                idempotencyKey: pending.idempotencyKey,
            )
            localStore.removeEvidence(id: pending.id)
            var evidence = confirmedEvidence(jobID: pending.jobID)
            evidence[pending.subtaskID] = result.evidence
            cache(evidence, name: "evidence.\(pending.jobID)")
            return result.evidence
        } catch {
            localStore.markEvidenceFailure(id: pending.id, error: error)
            throw error
        }
    }
}

struct RootView: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        Group {
            if let error = model.configurationError {
                ConfigurationView(error: error)
            } else if let session = model.session {
                switch session.user.role {
                case .client:
                    ClientWorkspaceView()
                case .worker:
                    WorkerWorkspaceView()
                case .admin:
                    AdminBoundaryView()
                }
            } else {
                LoginView()
            }
        }
        .background(NetworkPeerTheme.surface.ignoresSafeArea())
    }
}

@MainActor
final class DeviceLocationManager: NSObject, ObservableObject, @preconcurrency CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    private var continuation: CheckedContinuation<CLLocationCoordinate2D, Error>?
    private var timeoutTask: Task<Void, Never>?

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyNearestTenMeters
    }

    func requestCurrentCoordinate() async throws -> CLLocationCoordinate2D {
        guard CLLocationManager.locationServicesEnabled() else {
            throw NetworkPeerAPIError.transport("Location Services are disabled. Enable them in Settings and try again.")
        }
        guard continuation == nil else {
            throw NetworkPeerAPIError.transport("A location request is already in progress.")
        }
        return try await withCheckedThrowingContinuation { continuation in
            self.continuation = continuation
            requestLocationWhenAuthorized()
            scheduleTimeout()
        }
    }

    func openSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }

    func locationManagerDidChangeAuthorization(_: CLLocationManager) {
        requestLocationWhenAuthorized()
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let coordinate = locations.last?.coordinate else { return }
        finish(returning: coordinate)
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        finish(throwing: NetworkPeerAPIError.transport("A current location could not be obtained. Move outdoors and try again."))
    }

    private func requestLocationWhenAuthorized() {
        guard continuation != nil else { return }
        switch manager.authorizationStatus {
        case .notDetermined:
            manager.requestWhenInUseAuthorization()
        case .authorizedAlways, .authorizedWhenInUse:
            manager.requestLocation()
        case .denied, .restricted:
            finish(throwing: NetworkPeerAPIError.transport("Location permission is required to search nearby work. Enable it in Settings and try again."))
        @unknown default:
            finish(throwing: NetworkPeerAPIError.transport("Location permission could not be determined."))
        }
    }

    private func scheduleTimeout() {
        timeoutTask?.cancel()
        timeoutTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 15_000_000_000)
            guard !Task.isCancelled else { return }
            self?.finish(throwing: NetworkPeerAPIError.transport("Location timed out. Check Location Services and try again."))
        }
    }

    private func finish(returning coordinate: CLLocationCoordinate2D) {
        timeoutTask?.cancel()
        timeoutTask = nil
        continuation?.resume(returning: coordinate)
        continuation = nil
    }

    private func finish(throwing error: Error) {
        timeoutTask?.cancel()
        timeoutTask = nil
        continuation?.resume(throwing: error)
        continuation = nil
    }
}
