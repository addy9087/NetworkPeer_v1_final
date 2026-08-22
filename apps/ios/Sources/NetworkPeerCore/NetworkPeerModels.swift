import Foundation

public enum UserRole: String, Codable, CaseIterable, Hashable, Sendable {
    case client = "CLIENT"
    case worker = "WORKER"
    case admin = "ADMIN"
}

public enum JobStatus: String, Codable, CaseIterable, Hashable, Sendable {
    case funding = "FUNDING"
    case posted = "POSTED"
    case assigned = "ASSIGNED"
    case enRoute = "EN_ROUTE"
    case atLocation = "AT_LOCATION"
    case inProgress = "IN_PROGRESS"
    case submitted = "SUBMITTED"
    case approved = "APPROVED"
    case completed = "COMPLETED"
    case cancelled = "CANCELLED"
    case disputed = "DISPUTED"
}

public enum EscrowStatus: String, Codable, Hashable, Sendable {
    case unfunded = "UNFUNDED"
    case pending = "PENDING"
    case held = "HELD"
    case released = "RELEASED"
    case frozen = "FROZEN"
    case refunded = "REFUNDED"
}

public enum SubtaskStatus: String, Codable, Hashable, Sendable {
    case pending = "PENDING"
    case inProgress = "IN_PROGRESS"
    case completed = "COMPLETED"
    case skipped = "SKIPPED"
}

public enum MediaType: String, Codable, Hashable, Sendable {
    case image = "IMAGE"
    case video = "VIDEO"
    case audio = "AUDIO"
    case document = "DOCUMENT"
}

public enum MediaStatus: String, Codable, Hashable, Sendable {
    case pending = "PENDING"
    case uploaded = "UPLOADED"
    case verified = "VERIFIED"
    case rejected = "REJECTED"
}

public enum PaymentOperationStatus: String, Codable, Hashable, Sendable {
    case created = "CREATED"
    case pending = "PENDING"
    case succeeded = "SUCCEEDED"
    case failed = "FAILED"
    case cancelled = "CANCELLED"
}

public enum ClientJobResolutionAction: String, Codable, Equatable, Sendable {
    case complete = "COMPLETE"
    case dispute = "DISPUTE"
}

public enum JSONValue: Codable, Equatable, Sendable {
    case string(String)
    case number(Double)
    case bool(Bool)
    case object([String: JSONValue])
    case array([JSONValue])
    case null

    public init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if container.decodeNil() {
            self = .null
        } else if let value = try? container.decode(Bool.self) {
            self = .bool(value)
        } else if let value = try? container.decode(Double.self) {
            self = .number(value)
        } else if let value = try? container.decode(String.self) {
            self = .string(value)
        } else if let value = try? container.decode([String: JSONValue].self) {
            self = .object(value)
        } else if let value = try? container.decode([JSONValue].self) {
            self = .array(value)
        } else {
            throw DecodingError.dataCorruptedError(in: container, debugDescription: "Unsupported JSON value")
        }
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        switch self {
        case let .string(value): try container.encode(value)
        case let .number(value): try container.encode(value)
        case let .bool(value): try container.encode(value)
        case let .object(value): try container.encode(value)
        case let .array(value): try container.encode(value)
        case .null: try container.encodeNil()
        }
    }

    public var stringValue: String? {
        guard case let .string(value) = self else { return nil }
        return value
    }

    public var objectValue: [String: JSONValue]? {
        guard case let .object(value) = self else { return nil }
        return value
    }

    public subscript(key: String) -> JSONValue? {
        objectValue?[key]
    }
}

public struct APIError: Codable, Equatable, Sendable {
    public let code: String
    public let message: String
}

public struct APIEnvelope<Payload: Codable & Sendable>: Codable, Sendable {
    public let success: Bool
    public let data: Payload?
    public let error: APIError?
}

public enum NetworkPeerAPIError: LocalizedError, Sendable {
    case invalidConfiguration(String)
    case validation(String)
    case transport(String)
    case server(code: String, message: String, statusCode: Int)
    case invalidResponse
    case evidenceTooLarge
    case unsupportedMedia
    case unreadableEvidence

    public var errorDescription: String? {
        switch self {
        case let .invalidConfiguration(message), let .validation(message), let .transport(message): return message
        case let .server(code, message, _): return "\(code): \(message)"
        case .invalidResponse: return "NetworkPeer returned an invalid response."
        case .evidenceTooLarge: return "Evidence exceeds the configured platform limit."
        case .unsupportedMedia: return "Select a JPEG, PNG, WebP, supported video/audio file, or PDF."
        case .unreadableEvidence: return "The selected evidence file could not be read."
        }
    }
}

/// The release build supplies this public value from the same backend setting
/// that enforces evidence uploads. The fallback is only a development default;
/// each deployed environment must provide its authoritative limit.
public enum EvidenceUploadLimits {
    public static let defaultMaximumFileSizeBytes: Int64 = 25 * 1024 * 1024

    public static func isValid(_ value: Int64) -> Bool {
        value > 0
    }
}

public struct Point: Codable, Equatable, Sendable {
    public let type: String
    public let coordinates: [Double]

    public init(longitude: Double, latitude: Double) {
        self.type = "Point"
        self.coordinates = [longitude, latitude]
    }

    public var isValid: Bool {
        guard type == "Point", coordinates.count == 2 else { return false }
        let longitude = coordinates[0]
        let latitude = coordinates[1]
        return longitude.isFinite && latitude.isFinite
            && (-180 ... 180).contains(longitude)
            && (-90 ... 90).contains(latitude)
    }
}

public struct AuthUser: Codable, Equatable, Sendable {
    public let id: String
    public let role: UserRole
    public let phone: String
}

public struct TokenPair: Codable, Sendable {
    public let accessToken: String
    public let refreshToken: String
    public let expiresIn: Int
    public let user: AuthUser

    enum CodingKeys: String, CodingKey {
        case accessToken = "access_token"
        case refreshToken = "refresh_token"
        case expiresIn = "expires_in"
        case user
    }
}

public struct StoredSession: Codable, Equatable, Sendable {
    public let accessToken: String
    public let refreshToken: String
    public let expiresInSeconds: Int
    public let user: AuthUser

    public init(pair: TokenPair) {
        accessToken = pair.accessToken
        refreshToken = pair.refreshToken
        expiresInSeconds = pair.expiresIn
        user = pair.user
    }
}

public struct OTPDelivery: Codable, Sendable {
    public let transport: String?
    public let to: String?
}

public struct OTPRequestResult: Codable, Sendable {
    public let expiresInSeconds: Int
    public let otpLength: Int
    public let delivery: OTPDelivery
    public let otp: String?
}

public struct Job: Codable, Identifiable, Sendable {
    public let id: String
    public let clientID: String
    public let workerID: String?
    public let title: String
    public let description: String
    public let category: String
    public let status: JobStatus
    public let priority: Int
    public let budgetCents: Int64
    public let platformFeeCents: Int64
    public let currency: String
    public let escrowStatus: EscrowStatus
    public let fundedAt: String?
    public let location: Point
    public let address: String?
    public let scheduledAt: String?
    public let startedAt: String?
    public let completedAt: String?
    public let cancelledAt: String?
    public let cancellationReason: String?
    public let metadata: [String: JSONValue]
    public let createdAt: String
    public let updatedAt: String

    enum CodingKeys: String, CodingKey {
        case id
        case clientID = "client_id"
        case workerID = "worker_id"
        case title, description, category, status, priority, currency, location, address, metadata
        case budgetCents = "budget_cents"
        case platformFeeCents = "platform_fee_cents"
        case escrowStatus = "escrow_status"
        case fundedAt = "funded_at"
        case scheduledAt = "scheduled_at"
        case startedAt = "started_at"
        case completedAt = "completed_at"
        case cancelledAt = "cancelled_at"
        case cancellationReason = "cancellation_reason"
        case createdAt = "created_at"
        case updatedAt = "updated_at"
    }
}

public struct JobSubtask: Codable, Identifiable, Sendable {
    public let id: String
    public let jobID: String
    public let title: String
    public let description: String?
    public let sequenceOrder: Int
    public let isRequired: Bool
    public let status: SubtaskStatus
    public let completedAt: String?
    public let metadata: [String: JSONValue]
    public let createdAt: String
    public let updatedAt: String

    enum CodingKeys: String, CodingKey {
        case id, title, description, status, metadata
        case jobID = "job_id"
        case sequenceOrder = "sequence_order"
        case isRequired = "is_required"
        case completedAt = "completed_at"
        case createdAt = "created_at"
        case updatedAt = "updated_at"
    }
}

public struct ClientJobDetail: Codable, Sendable {
    public let job: Job
    public let subtasks: [JobSubtask]
}

public struct ClientJobPage: Codable, Sendable {
    public let items: [Job]
    public let total: Int
    public let page: Int
    public let perPage: Int
}

public struct WorkerJobSummary: Codable, Identifiable, Sendable {
    public let id: String
    public let title: String
    public let description: String
    public let category: String
    public let priority: Int
    public let budgetCents: Int64
    public let currency: String
    public let scheduledAt: String?
    public let createdAt: String
    public let distanceBand: String

    enum CodingKeys: String, CodingKey {
        case id, title, description, category, priority, currency
        case budgetCents = "budget_cents"
        case scheduledAt = "scheduled_at"
        case createdAt = "created_at"
        case distanceBand = "distance_band"
    }
}

public struct WorkerJobDetail: Codable, Identifiable, Sendable {
    public let id: String
    public let title: String
    public let description: String
    public let category: String
    public let status: JobStatus
    public let priority: Int
    public let budgetCents: Int64
    public let currency: String
    public let scheduledAt: String?
    public let createdAt: String
    public let updatedAt: String
    public let location: Point?
    public let address: String?
    public let isAssignedToRequester: Bool
    public let subtasks: [JobSubtask]

    enum CodingKeys: String, CodingKey {
        case id, title, description, category, status, priority, currency, location, address, subtasks
        case budgetCents = "budget_cents"
        case scheduledAt = "scheduled_at"
        case createdAt = "created_at"
        case updatedAt = "updated_at"
        case isAssignedToRequester = "is_assigned_to_requester"
    }
}

public struct NearbyJobsPage: Codable, Sendable {
    public let items: [WorkerJobSummary]
    public let page: Int
    public let perPage: Int
    public let radiusKilometres: Int
    public let hasMore: Bool
    public let nextPage: Int?

    enum CodingKeys: String, CodingKey {
        case items, page
        case perPage
        case radiusKilometres = "radius_km"
        case hasMore = "has_more"
        case nextPage = "next_page"
    }
}

public struct WalletBalance: Codable, Identifiable, Sendable {
    public let currency: String
    public let availableBalanceCents: String
    public let pendingEscrowCents: String
    public let lifetimeEarningsCents: String
    public let lifetimeSpendCents: String

    public var id: String { currency }
}

public struct WalletResponse: Codable, Sendable {
    public let balances: [WalletBalance]
}

public struct FundingResult: Codable, Sendable {
    public let operationID: String
    public let ledgerTransactionID: String
    public let amountCents: String
    public let currency: String
    public let status: PaymentOperationStatus
    public let dispatchRequired: Bool
    public let providerReference: String?
    public let clientSecret: String?

    enum CodingKeys: String, CodingKey {
        case operationID = "operationId"
        case ledgerTransactionID = "ledgerTransactionId"
        case amountCents, currency, status, dispatchRequired, providerReference, clientSecret
    }
}

public struct ApprovalResult: Codable, Sendable {
    public let jobID: String
    public let status: JobStatus
    public let settlementLedgerTransactionID: String
    public let payoutOperationID: String
    public let payoutAmountCents: String
    public let currency: String
    public let payoutStatus: PaymentOperationStatus
    public let payoutProviderReference: String?
    public let payoutDispatchPending: Bool

    enum CodingKeys: String, CodingKey {
        case jobID = "jobId"
        case status
        case settlementLedgerTransactionID = "settlementLedgerTransactionId"
        case payoutOperationID = "payoutOperationId"
        case payoutAmountCents, currency, payoutStatus, payoutProviderReference, payoutDispatchPending
    }
}

public struct EvidenceSummary: Codable, Identifiable, Sendable {
    public let id: String
    public let jobID: String
    public let subtaskID: String
    public let mediaType: MediaType
    public let mimeType: String?
    public let fileSizeBytes: Int64?
    public let capturedAt: String
    public let uploadedAt: String?
    public let status: MediaStatus

    enum CodingKeys: String, CodingKey {
        case id, status
        case jobID = "job_id"
        case subtaskID = "subtask_id"
        case mediaType = "media_type"
        case mimeType = "mime_type"
        case fileSizeBytes = "file_size_bytes"
        case capturedAt = "captured_at"
        case uploadedAt = "uploaded_at"
    }
}

public struct EvidenceUploadTarget: Codable, Sendable {
    public let url: URL
    public let fields: [String: String]
    public let expiresAt: String

    enum CodingKeys: String, CodingKey {
        case url, fields
        case expiresAt = "expires_at"
    }
}

public struct EvidenceDownloadTarget: Codable, Sendable {
    public let url: URL
    public let expiresAt: String

    public var isHTTPS: Bool {
        url.scheme?.lowercased() == "https" && url.host != nil
    }

    enum CodingKeys: String, CodingKey {
        case url
        case expiresAt = "expires_at"
    }
}

/// Client evidence review uses an API-issued, short-lived download target. The
/// target must be treated as opaque and is intentionally never persisted.
public struct ClientEvidenceReview: Codable, Identifiable, Sendable {
    public let id: String
    public let jobID: String
    public let subtaskID: String
    public let mediaType: MediaType
    public let mimeType: String?
    public let fileSizeBytes: Int64?
    public let capturedAt: String
    public let uploadedAt: String
    public let status: MediaStatus
    public let download: EvidenceDownloadTarget

    enum CodingKeys: String, CodingKey {
        case id, status, download
        case jobID = "job_id"
        case subtaskID = "subtask_id"
        case mediaType = "media_type"
        case mimeType = "mime_type"
        case fileSizeBytes = "file_size_bytes"
        case capturedAt = "captured_at"
        case uploadedAt = "uploaded_at"
    }
}

public struct ClientEvidenceReviewResponse: Codable, Sendable {
    public let evidence: [ClientEvidenceReview]
}

public struct EvidenceReservation: Codable, Sendable {
    public let evidence: EvidenceSummary
    public let upload: EvidenceUploadTarget?
}

public struct WorkStatusResult: Codable, Sendable {
    public let jobID: String
    public let status: JobStatus

    enum CodingKeys: String, CodingKey {
        case jobID = "job_id"
        case status
    }
}

public struct SubmitWorkResult: Codable, Sendable {
    public let jobID: String
    public let status: JobStatus

    enum CodingKeys: String, CodingKey {
        case jobID = "job_id"
        case status
    }
}

public struct ClientJobCancelResult: Codable, Sendable {
    public let job: Job
    public let cancelled: Bool
}

public struct ClientJobResolutionResult: Codable, Sendable {
    public let job: Job
    public let action: ClientJobResolutionAction
}

public struct SyncEvent: Codable, Identifiable, Sendable {
    public let cursor: String
    public let eventID: String
    public let topic: String
    public let entityType: String
    public let entityID: String?
    public let payload: [String: JSONValue]
    public let notification: SyncNotification?
    public let createdAt: String

    public var id: String { eventID }

    enum CodingKeys: String, CodingKey {
        case cursor, topic, payload, notification
        case eventID = "event_id"
        case entityType = "entity_type"
        case entityID = "entity_id"
        case createdAt = "created_at"
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        cursor = try container.decode(String.self, forKey: .cursor)
        eventID = try container.decode(String.self, forKey: .eventID)
        topic = try container.decode(String.self, forKey: .topic)
        entityType = try container.decode(String.self, forKey: .entityType)
        entityID = try container.decodeIfPresent(String.self, forKey: .entityID)
        payload = try container.decode([String: JSONValue].self, forKey: .payload)
        // Older sync deployments may include a partial notification projection.
        notification = try? container.decode(SyncNotification.self, forKey: .notification)
        createdAt = try container.decode(String.self, forKey: .createdAt)
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(cursor, forKey: .cursor)
        try container.encode(eventID, forKey: .eventID)
        try container.encode(topic, forKey: .topic)
        try container.encode(entityType, forKey: .entityType)
        try container.encodeIfPresent(entityID, forKey: .entityID)
        try container.encode(payload, forKey: .payload)
        try container.encodeIfPresent(notification, forKey: .notification)
        try container.encode(createdAt, forKey: .createdAt)
    }
}

/// A compact notification projection may accompany a sync event.
public struct SyncNotification: Codable, Identifiable, Sendable {
    public let id: String
    public let title: String
    public let body: String
    public let readAt: String?

    enum CodingKeys: String, CodingKey {
        case id, title, body
        case readAt = "read_at"
    }
}

public struct NetworkPeerNotification: Codable, Identifiable, Sendable {
    public let id: String
    public let cursor: String
    public let topic: String
    public let title: String
    public let body: String
    public let data: [String: JSONValue]
    public let readAt: String?
    public let createdAt: String

    enum CodingKeys: String, CodingKey {
        case id, cursor, topic, title, body, data
        case readAt = "read_at"
        case createdAt = "created_at"
    }
}

public struct NotificationPage: Codable, Sendable {
    public let items: [NetworkPeerNotification]
    public let hasMore: Bool
    public let nextCursor: String?

    enum CodingKeys: String, CodingKey {
        case items
        case hasMore = "has_more"
        case nextCursor = "next_cursor"
    }
}

public struct MarkAllNotificationsReadResult: Codable, Sendable {
    public let markedCount: Int

    enum CodingKeys: String, CodingKey {
        case markedCount = "marked_count"
    }
}

public struct WalletLedgerEntry: Codable, Identifiable, Sendable {
    public let id: String
    public let userID: String
    public let jobID: String?
    public let transactionType: String
    public let transactionStatus: String
    public let amountCents: Int64
    public let balanceAfterCents: Int64
    public let currency: String
    public let referenceID: String?
    public let referenceType: String?
    public let description: String
    public let metadata: [String: JSONValue]
    public let idempotencyKey: String?
    public let processedAt: String?
    public let createdAt: String

    enum CodingKeys: String, CodingKey {
        case id, currency, description, metadata
        case userID = "user_id"
        case jobID = "job_id"
        case transactionType = "transaction_type"
        case transactionStatus = "transaction_status"
        case amountCents = "amount_cents"
        case balanceAfterCents = "balance_after_cents"
        case referenceID = "reference_id"
        case referenceType = "reference_type"
        case idempotencyKey = "idempotency_key"
        case processedAt = "processed_at"
        case createdAt = "created_at"
    }
}

public struct SyncPage: Codable, Sendable {
    public let events: [SyncEvent]
    public let hasMore: Bool
    public let nextCursor: String

    enum CodingKeys: String, CodingKey {
        case events
        case hasMore = "has_more"
        case nextCursor = "next_cursor"
    }
}

public struct WorkerSyncPage: Codable, Sendable {
    public let events: [SyncEvent]
    public let jobs: [WorkerJobDetail]
    public let snapshotJobs: [WorkerJobDetail]
    public let ledgerEntries: [WalletLedgerEntry]
    public let removedJobIDs: [String]
    public let hasMore: Bool
    public let nextCursor: String

    enum CodingKeys: String, CodingKey {
        case events, jobs
        case snapshotJobs = "snapshot_jobs"
        case ledgerEntries = "ledger_entries"
        case removedJobIDs = "removed_job_ids"
        case hasMore = "has_more"
        case nextCursor = "next_cursor"
    }
}
