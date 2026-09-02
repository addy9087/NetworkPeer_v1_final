import Foundation
import Testing
@testable import NetworkPeerCore

@Suite("NetworkPeer mobile contract")
struct ContractTests {
    @Test("Token pairs use the backend snake_case wire format")
    func tokenPairUsesTheBackendSnakeCaseWireFormat() throws {
        let data = Data("""
        {
          "success": true,
          "data": {
            "access_token": "access",
            "refresh_token": "refresh",
            "expires_in": 900,
            "user": { "id": "d1f61aee-3b00-4b53-a6d9-8d1e9a94aeb1", "role": "WORKER", "phone": "+15551234567" }
          },
          "error": null
        }
        """.utf8)

        let envelope = try JSONDecoder().decode(APIEnvelope<TokenPair>.self, from: data)
        #expect(envelope.success)
        #expect(envelope.data?.accessToken == "access")
        #expect(envelope.data?.user.role == .worker)
    }

    @Test("OTP requests use the Cognito challenge wire format")
    func otpRequestUsesCognitoChallengeWireFormat() throws {
        let data = Data("""
        {
          "challenge_id": "cognito-challenge",
          "expires_in_seconds": 600,
          "otp_length": 6,
          "delivery": { "transport": "sms", "to": "+15551234567" }
        }
        """.utf8)

        let result = try JSONDecoder().decode(OTPRequestResult.self, from: data)
        #expect(result.challengeId == "cognito-challenge")
        #expect(result.expiresInSeconds == 600)
        #expect(result.otpLength == 6)
        #expect(result.delivery.transport == "sms")
        #expect(result.delivery.to == "+15551234567")
    }

    @Test("GeoJSON points preserve longitude before latitude")
    func pointPreservesLongitudeBeforeLatitude() {
        let point = Point(longitude: -73.9857, latitude: 40.7484)
        #expect(point.coordinates == [-73.9857, 40.7484])
        #expect(point.isValid)
    }

    @Test("Job creation preserves optional privacy-safe fields and idempotency")
    func jobCreationEncodesPrivacyAndIdempotencyFields() throws {
        let request = CreateJobRequest(
            title: "Replace outlet",
            description: "Replace one damaged outlet cover.",
            category: "Electrical",
            budgetCents: 12_500,
            currency: "USD",
            location: Point(longitude: -73.9857, latitude: 40.7484),
            address: "350 Fifth Avenue",
            metadata: ["source": .string("IOS")],
            publicTitle: "Small electrical task",
            publicDescription: "A short indoor repair task.",
            idempotencyKey: "6b978d52-c078-4266-9697-1f1d41d984f0",
            subtasks: [CreateSubtaskRequest(title: "Photo of completed cover", isRequired: true)],
        )

        try CreateJobValidator.validate(request)
        let object = try JSONSerialization.jsonObject(with: JSONEncoder().encode(request)) as? [String: Any]
        #expect(object?["idempotency_key"] as? String == "6b978d52-c078-4266-9697-1f1d41d984f0")
        #expect(object?["public_title"] as? String == "Small electrical task")
        #expect((object?["subtasks"] as? [[String: Any]])?.first?["is_required"] as? Bool == true)
        let restored = try JSONDecoder().decode(CreateJobRequest.self, from: JSONEncoder().encode(request))
        #expect(restored.idempotencyKey == request.idempotencyKey)
    }

    @Test("Invalid evidence metadata is rejected before a reservation request")
    func invalidEvidenceMetadataIsRejected() {
        let request = ReserveEvidenceRequest(
            jobID: "job",
            subtaskID: "subtask",
            mediaType: .image,
            mimeType: "image/jpeg",
            fileSizeBytes: 20,
            capturedAt: "2026-08-22T10:00:00Z",
            checksumSHA256: "NOT-A-SHA",
            idempotencyKey: "retry-key",
        )

        #expect(throws: NetworkPeerAPIError.self) {
            try EvidenceReservationValidator.validate(request)
        }
    }

    @Test("Evidence reservation honors the configured backend byte limit")
    func evidenceReservationHonorsConfiguredByteLimit() {
        let request = ReserveEvidenceRequest(
            jobID: "job",
            subtaskID: "subtask",
            mediaType: .image,
            mimeType: "image/jpeg",
            fileSizeBytes: 20,
            capturedAt: "2026-08-22T10:00:00Z",
            checksumSHA256: String(repeating: "a", count: 64),
            idempotencyKey: "retry-key",
        )

        #expect(throws: NetworkPeerAPIError.self) {
            try EvidenceReservationValidator.validate(request, maximumFileSizeBytes: 19)
        }
    }

    @Test("Evidence fallback matches the backend's 25 MiB default")
    func evidenceFallbackMatchesBackendDefault() {
        #expect(EvidenceUploadLimits.defaultMaximumFileSizeBytes == 25 * 1024 * 1024)
    }

    @Test("WebM evidence metadata keeps audio and video distinct")
    func webMEvidenceMetadataKeepsAudioAndVideoDistinct() throws {
        let metadata = (
            jobID: "job",
            subtaskID: "subtask",
            fileSizeBytes: Int64(20),
            capturedAt: "2026-08-22T10:00:00Z",
            checksumSHA256: String(repeating: "a", count: 64),
            idempotencyKey: "retry-key"
        )
        let audio = ReserveEvidenceRequest(
            jobID: metadata.jobID,
            subtaskID: metadata.subtaskID,
            mediaType: .audio,
            mimeType: "audio/webm",
            fileSizeBytes: metadata.fileSizeBytes,
            capturedAt: metadata.capturedAt,
            checksumSHA256: metadata.checksumSHA256,
            idempotencyKey: metadata.idempotencyKey,
        )
        let video = ReserveEvidenceRequest(
            jobID: metadata.jobID,
            subtaskID: metadata.subtaskID,
            mediaType: .video,
            mimeType: "video/webm",
            fileSizeBytes: metadata.fileSizeBytes,
            capturedAt: metadata.capturedAt,
            checksumSHA256: metadata.checksumSHA256,
            idempotencyKey: metadata.idempotencyKey,
        )

        try EvidenceReservationValidator.validate(audio)
        try EvidenceReservationValidator.validate(video)
    }

    @Test("Client evidence review decodes an opaque API-issued download target")
    func clientEvidenceReviewDecodesDownloadTarget() throws {
        let data = Data("""
        {
          "success": true,
          "data": {
            "evidence": [{
              "id": "d1f61aee-3b00-4b53-a6d9-8d1e9a94aeb1",
              "job_id": "cc128fb6-39c9-4b8d-bf55-a52d0692aaf2",
              "subtask_id": "56428b73-d7f6-4c18-a1fc-1ef75b983cee",
              "media_type": "IMAGE",
              "mime_type": "image/jpeg",
              "file_size_bytes": 1234,
              "captured_at": "2026-08-22T10:00:00Z",
              "uploaded_at": "2026-08-22T10:01:00Z",
              "status": "VERIFIED",
              "download": {
                "url": "https://storage.example.test/evidence",
                "expires_at": "2026-08-22T10:11:00Z"
              }
            }]
          },
          "error": null
        }
        """.utf8)

        let envelope = try JSONDecoder().decode(APIEnvelope<ClientEvidenceReviewResponse>.self, from: data)
        #expect(envelope.data?.evidence.first?.status == .verified)
        #expect(envelope.data?.evidence.first?.download.url.host == "storage.example.test")
    }

    @Test("Client evidence review opens only HTTPS download targets")
    func clientEvidenceReviewRequiresHTTPSDownloadTarget() throws {
        let decoder = JSONDecoder()
        let secure = try decoder.decode(EvidenceDownloadTarget.self, from: Data("""
        { "url": "https://storage.example.test/evidence", "expires_at": "2026-08-22T10:11:00Z" }
        """.utf8))
        let insecure = try decoder.decode(EvidenceDownloadTarget.self, from: Data("""
        { "url": "http://storage.example.test/evidence", "expires_at": "2026-08-22T10:11:00Z" }
        """.utf8))

        #expect(secure.isHTTPS)
        #expect(!insecure.isHTTPS)
    }

    @Test("Remote notification and worker sync pages use the v0.2 field names")
    func remoteNotificationAndWorkerSyncDecode() throws {
        let notificationData = Data("""
        {
          "items": [{
            "id": "d1f61aee-3b00-4b53-a6d9-8d1e9a94aeb1",
            "cursor": "25",
            "topic": "JOB_STATUS_CHANGED",
            "title": "Job updated",
            "body": "Your job changed status.",
            "data": { "job_id": "cc128fb6-39c9-4b8d-bf55-a52d0692aaf2" },
            "read_at": null,
            "created_at": "2026-08-22T10:00:00Z"
          }],
          "has_more": true,
          "next_cursor": "24"
        }
        """.utf8)
        let notificationPage = try JSONDecoder().decode(NotificationPage.self, from: notificationData)
        #expect(notificationPage.items.first?.data["job_id"]?.stringValue == "cc128fb6-39c9-4b8d-bf55-a52d0692aaf2")
        #expect(notificationPage.nextCursor == "24")

        let workerSyncData = Data("""
        {
          "events": [],
          "jobs": [],
          "snapshot_jobs": [],
          "ledger_entries": [],
          "removed_job_ids": ["cc128fb6-39c9-4b8d-bf55-a52d0692aaf2"],
          "has_more": false,
          "next_cursor": "26"
        }
        """.utf8)
        let workerPage = try JSONDecoder().decode(WorkerSyncPage.self, from: workerSyncData)
        #expect(workerPage.removedJobIDs.count == 1)
        #expect(workerPage.nextCursor == "26")
    }

    @Test("Data-only pushes require complete top-level fields and the active recipient")
    func dataOnlyPushPayloadRequiresMatchingRecipient() {
        let userInfo: [AnyHashable: Any] = [
            "recipient_user_id": "d1f61aee-3b00-4b53-a6d9-8d1e9a94aeb1",
            "title": "Job updated",
            "body": "Your job changed status.",
            "cursor": "25",
            "topic": "JOB_STATUS_CHANGED",
            "job_id": "cc128fb6-39c9-4b8d-bf55-a52d0692aaf2",
        ]

        let payload = DataOnlyPushPayload(userInfo: userInfo)
        #expect(payload?.jobID == "cc128fb6-39c9-4b8d-bf55-a52d0692aaf2")
        #expect(payload?.isForActiveSession(testSession(accessToken: "access", refreshToken: "refresh")) == true)
        #expect(payload?.isForActiveSession(nil) == false)
        #expect(payload?.isForActiveSession(StoredSession(pair: TokenPair(
            accessToken: "access",
            refreshToken: "refresh",
            expiresIn: 900,
            user: AuthUser(id: "other-user", role: .worker, phone: "+15551234567"),
        ))) == false)

        var inboxPayload = userInfo
        inboxPayload[AnyHashable("job_id")] = nil
        #expect(DataOnlyPushPayload(userInfo: inboxPayload)?.jobID == nil)

        #expect(DataOnlyPushPayload(userInfo: [
            "data": userInfo,
        ]) == nil)
        #expect(DataOnlyPushPayload(userInfo: [
            "recipient_user_id": "d1f61aee-3b00-4b53-a6d9-8d1e9a94aeb1",
            "title": "Job updated",
            "body": "Your job changed status.",
            "cursor": "not-a-cursor",
            "topic": "JOB_STATUS_CHANGED",
        ]) == nil)
        #expect(DataOnlyPushPayload(userInfo: userInfo.merging([
            "aps": ["alert": "Do not render this remotely"],
        ]) { _, replacement in replacement }) == nil)
    }

    @Test("Sync remains usable when an older server includes a partial notification projection")
    func syncEventToleratesPartialNotificationProjection() throws {
        let data = Data("""
        {
          "cursor": "17",
          "event_id": "d1f61aee-3b00-4b53-a6d9-8d1e9a94aeb1",
          "topic": "JOB_STATUS_CHANGED",
          "entity_type": "job",
          "entity_id": "cc128fb6-39c9-4b8d-bf55-a52d0692aaf2",
          "payload": { "title": "Job updated" },
          "notification": { "id": "legacy" },
          "created_at": "2026-08-22T10:00:00Z"
        }
        """.utf8)

        let event = try JSONDecoder().decode(SyncEvent.self, from: data)
        #expect(event.notification == nil)
        #expect(event.cursor == "17")
    }

    @Test("Sync events decode the compact notification projection")
    func syncEventDecodesCompactNotificationProjection() throws {
        let data = Data("""
        {
          "cursor": "18",
          "event_id": "d1f61aee-3b00-4b53-a6d9-8d1e9a94aeb1",
          "topic": "SYSTEM",
          "entity_type": "notification",
          "entity_id": null,
          "payload": {},
          "notification": {
            "id": "56428b73-d7f6-4c18-a1fc-1ef75b983cee",
            "title": "NetworkPeer update",
            "body": "A new update is ready.",
            "read_at": null
          },
          "created_at": "2026-08-22T10:00:00Z"
        }
        """.utf8)

        let event = try JSONDecoder().decode(SyncEvent.self, from: data)
        #expect(event.notification?.title == "NetworkPeer update")
    }
}

@Suite("Authentication refresh")
struct AuthenticationRefreshTests {
    @Test("Concurrent 401 responses share one refresh token request")
    func concurrentUnauthorizedRequestsShareOneRefresh() async throws {
        let state = RefreshURLProtocol.state
        state.reset()
        let initial = testSession(accessToken: "expired", refreshToken: "one-time-refresh")
        let store = InMemorySessionStore(initial)
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [RefreshURLProtocol.self]
        let urlSession = URLSession(configuration: configuration)
        defer {
            state.releaseRefresh()
            urlSession.invalidateAndCancel()
        }
        let api = try NetworkPeerAPI(
            baseURL: URL(string: "https://auth-refresh.test/api/v1/")!,
            sessionStore: store,
            urlSession: urlSession,
        )

        async let first: ClientJobPage = api.clientJobs()
        try await waitUntil { state.refreshRequestCount == 1 }
        let second = Task<ClientJobPage, Error> { try await api.clientJobs() }
        try await waitUntil { state.expiredJobRequestCount == 2 }
        state.releaseRefresh()

        let firstPage = try await first
        let secondPage = try await second.value
        #expect(firstPage.items.isEmpty)
        #expect(secondPage.items.isEmpty)
        #expect(state.refreshRequestCount == 1)
        #expect(store.read()?.accessToken == "fresh")
    }

    @Test("Conditional session changes preserve a newer session")
    func conditionalSessionChangesPreserveNewerSession() throws {
        let initial = testSession(accessToken: "expired", refreshToken: "one-time-refresh")
        let newer = testSession(accessToken: "newer", refreshToken: "newer-refresh")
        let refreshed = testSession(accessToken: "fresh", refreshToken: "fresh-refresh")
        let store = InMemorySessionStore(initial)

        try store.save(newer)
        let replaced = try store.replace(refreshed, ifMatches: initial)
        let cleared = try store.clear(ifMatches: initial)

        #expect(!replaced)
        #expect(!cleared)
        #expect(store.read() == newer)
    }

    @Test("Logout revokes with the refresh token without an access bearer")
    func logoutUsesRefreshTokenWithoutAccessBearer() async throws {
        let state = LogoutURLProtocol.state
        state.reset()
        let store = InMemorySessionStore(testSession(accessToken: "expired", refreshToken: "one-time-refresh"))
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [LogoutURLProtocol.self]
        let urlSession = URLSession(configuration: configuration)
        defer { urlSession.invalidateAndCancel() }
        let api = try NetworkPeerAPI(
            baseURL: URL(string: "https://logout.test/api/v1/")!,
            sessionStore: store,
            urlSession: urlSession,
        )

        await api.logout()

        guard let request = state.request else {
            Issue.record("Expected a logout request.")
            return
        }
        #expect(request.httpMethod == "POST")
        #expect(request.url?.path == "/api/v1/auth/logout")
        #expect(request.value(forHTTPHeaderField: "Authorization") == nil)
        let body = try JSONSerialization.jsonObject(with: state.body ?? Data()) as? [String: Any]
        #expect(body?["refresh_token"] as? String == "one-time-refresh")
        #expect(store.read() == nil)
    }
}

@Suite("Device registration")
struct DeviceRegistrationTests {
    @Test("Device unregistration sends a token-only DELETE request")
    func unregisterDeviceSendsTokenOnlyDeleteRequest() async throws {
        let state = DeviceUnregisterURLProtocol.state
        state.reset()
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [DeviceUnregisterURLProtocol.self]
        let urlSession = URLSession(configuration: configuration)
        defer { urlSession.invalidateAndCancel() }
        let api = try NetworkPeerAPI(
            baseURL: URL(string: "https://device-registration.test/api/v1/")!,
            sessionStore: InMemorySessionStore(testSession(accessToken: "access", refreshToken: "refresh")),
            urlSession: urlSession,
        )

        try await api.unregisterDevice(token: "fcm-registration-token")

        guard let request = state.request else {
            Issue.record("Expected an unregistration request.")
            return
        }
        #expect(request.httpMethod == "DELETE")
        #expect(request.url?.path == "/api/v1/notifications/devices")
        #expect(request.value(forHTTPHeaderField: "Authorization") == "Bearer access")
        let body = try JSONSerialization.jsonObject(with: state.body ?? Data()) as? [String: Any]
        #expect(body?["token"] as? String == "fcm-registration-token")
        #expect(body?["platform"] == nil)
    }
}

private func testSession(accessToken: String, refreshToken: String) -> StoredSession {
    StoredSession(pair: TokenPair(
        accessToken: accessToken,
        refreshToken: refreshToken,
        expiresIn: 900,
        user: AuthUser(id: "d1f61aee-3b00-4b53-a6d9-8d1e9a94aeb1", role: .worker, phone: "+15551234567"),
    ))
}

private enum RefreshProtocolResponse {
    case expiredJob
    case freshJob
    case refresh
    case notFound
}

private final class RefreshProtocolState: @unchecked Sendable {
    private let lock = NSLock()
    private var refreshGate = DispatchSemaphore(value: 0)
    private var refreshRequests = 0
    private var expiredJobRequests = 0

    var refreshRequestCount: Int {
        lock.lock()
        defer { lock.unlock() }
        return refreshRequests
    }

    var expiredJobRequestCount: Int {
        lock.lock()
        defer { lock.unlock() }
        return expiredJobRequests
    }

    func reset() {
        lock.lock()
        defer { lock.unlock() }
        refreshGate = DispatchSemaphore(value: 0)
        refreshRequests = 0
        expiredJobRequests = 0
    }

    func response(for request: URLRequest) -> RefreshProtocolResponse {
        lock.lock()
        defer { lock.unlock() }
        switch request.url?.path {
        case "/api/v1/client/jobs":
            if request.value(forHTTPHeaderField: "Authorization") == "Bearer expired" {
                expiredJobRequests += 1
                return .expiredJob
            }
            return .freshJob
        case "/api/v1/auth/refresh":
            refreshRequests += 1
            return .refresh
        default:
            return .notFound
        }
    }

    func waitForRefreshRelease() {
        lock.lock()
        let gate = refreshGate
        lock.unlock()
        _ = gate.wait(timeout: .now() + 5)
    }

    func releaseRefresh() {
        lock.lock()
        let gate = refreshGate
        lock.unlock()
        gate.signal()
    }
}

private final class RefreshURLProtocol: URLProtocol, @unchecked Sendable {
    static let state = RefreshProtocolState()

    override class func canInit(with request: URLRequest) -> Bool {
        request.url?.host == "auth-refresh.test"
    }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest {
        request
    }

    override func startLoading() {
        let response = Self.state.response(for: request)
        if response == .refresh {
            DispatchQueue.global().async { [weak self] in
                Self.state.waitForRefreshRelease()
                self?.send(response)
            }
        } else {
            send(response)
        }
    }

    override func stopLoading() {}

    private func send(_ response: RefreshProtocolResponse) {
        guard let url = request.url else { return }
        let statusCode: Int
        let body: String
        switch response {
        case .expiredJob:
            statusCode = 401
            body = #"{"success":false,"data":null,"error":{"code":"UNAUTHENTICATED","message":"Expired"}}"#
        case .freshJob:
            statusCode = 200
            body = #"{"success":true,"data":{"items":[],"total":0,"page":1,"perPage":20},"error":null}"#
        case .refresh:
            statusCode = 200
            body = #"{"success":true,"data":{"access_token":"fresh","refresh_token":"fresh-refresh","expires_in":900,"user":{"id":"d1f61aee-3b00-4b53-a6d9-8d1e9a94aeb1","role":"WORKER","phone":"+15551234567"}},"error":null}"#
        case .notFound:
            statusCode = 404
            body = #"{"success":false,"data":null,"error":{"code":"NOT_FOUND","message":"Not found"}}"#
        }
        guard let http = HTTPURLResponse(
            url: url,
            statusCode: statusCode,
            httpVersion: "HTTP/1.1",
            headerFields: ["Content-Type": "application/json"],
        ) else {
            return
        }
        client?.urlProtocol(self, didReceive: http, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Data(body.utf8))
        client?.urlProtocolDidFinishLoading(self)
    }
}

private final class DeviceUnregisterProtocolState: @unchecked Sendable {
    private let lock = NSLock()
    private var capturedRequest: URLRequest?
    private var capturedBody: Data?

    var request: URLRequest? {
        lock.lock()
        defer { lock.unlock() }
        return capturedRequest
    }

    var body: Data? {
        lock.lock()
        defer { lock.unlock() }
        return capturedBody
    }

    func reset() {
        lock.lock()
        defer { lock.unlock() }
        capturedRequest = nil
        capturedBody = nil
    }

    func record(_ request: URLRequest, body: Data?) {
        lock.lock()
        defer { lock.unlock() }
        capturedRequest = request
        capturedBody = body
    }
}

private final class DeviceUnregisterURLProtocol: URLProtocol, @unchecked Sendable {
    static let state = DeviceUnregisterProtocolState()

    override class func canInit(with request: URLRequest) -> Bool {
        request.url?.host == "device-registration.test"
    }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest {
        request
    }

    override func startLoading() {
        Self.state.record(request, body: requestBody())
        guard let url = request.url,
              let response = HTTPURLResponse(
                  url: url,
                  statusCode: 200,
                  httpVersion: "HTTP/1.1",
                  headerFields: ["Content-Type": "application/json"],
              ) else {
            return
        }
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Data(#"{"success":true,"data":{"deleted":true},"error":null}"#.utf8))
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}

    private func requestBody() -> Data? {
        if let body = request.httpBody { return body }
        guard let stream = request.httpBodyStream else { return nil }
        stream.open()
        defer { stream.close() }

        var body = Data()
        var buffer = [UInt8](repeating: 0, count: 1_024)
        while stream.hasBytesAvailable {
            let count = stream.read(&buffer, maxLength: buffer.count)
            guard count > 0 else { break }
            body.append(contentsOf: buffer[0 ..< count])
        }
        return body
    }
}

private final class LogoutProtocolState: @unchecked Sendable {
    private let lock = NSLock()
    private var capturedRequest: URLRequest?
    private var capturedBody: Data?

    var request: URLRequest? {
        lock.lock()
        defer { lock.unlock() }
        return capturedRequest
    }

    var body: Data? {
        lock.lock()
        defer { lock.unlock() }
        return capturedBody
    }

    func reset() {
        lock.lock()
        defer { lock.unlock() }
        capturedRequest = nil
        capturedBody = nil
    }

    func record(_ request: URLRequest, body: Data?) {
        lock.lock()
        defer { lock.unlock() }
        capturedRequest = request
        capturedBody = body
    }
}

private final class LogoutURLProtocol: URLProtocol, @unchecked Sendable {
    static let state = LogoutProtocolState()

    override class func canInit(with request: URLRequest) -> Bool {
        request.url?.host == "logout.test"
    }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest {
        request
    }

    override func startLoading() {
        Self.state.record(request, body: requestBody())
        guard let url = request.url,
              let response = HTTPURLResponse(
                  url: url,
                  statusCode: 200,
                  httpVersion: "HTTP/1.1",
                  headerFields: ["Content-Type": "application/json"],
              ) else {
            return
        }
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Data(#"{"success":true,"data":{"logged_out":true},"error":null}"#.utf8))
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}

    private func requestBody() -> Data? {
        if let body = request.httpBody { return body }
        guard let stream = request.httpBodyStream else { return nil }
        stream.open()
        defer { stream.close() }

        var body = Data()
        var buffer = [UInt8](repeating: 0, count: 1_024)
        while stream.hasBytesAvailable {
            let count = stream.read(&buffer, maxLength: buffer.count)
            guard count > 0 else { break }
            body.append(contentsOf: buffer[0 ..< count])
        }
        return body
    }
}

private enum AuthenticationRefreshTestError: Error {
    case timedOut
}

private func waitUntil(_ condition: @escaping @Sendable () -> Bool) async throws {
    for _ in 0 ..< 100 {
        if condition() { return }
        try await Task.sleep(nanoseconds: 10_000_000)
    }
    throw AuthenticationRefreshTestError.timedOut
}
