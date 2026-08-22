import Combine
import Foundation
import SwiftData

@Model
final class CachedPayloadRecord {
    @Attribute(.unique) var key: String
    var payload: Data
    var updatedAt: Date

    init(key: String, payload: Data, updatedAt: Date = .now) {
        self.key = key
        self.payload = payload
        self.updatedAt = updatedAt
    }
}

@Model
final class PendingEvidenceRecord {
    @Attribute(.unique) var id: String
    var accountID: String
    var jobID: String
    var subtaskID: String
    var localPath: String
    var capturedAtISO8601: String
    var locationData: Data?
    var idempotencyKey: String
    var retryCount: Int
    var lastError: String?
    var createdAt: Date

    init(
        id: String,
        accountID: String,
        jobID: String,
        subtaskID: String,
        localPath: String,
        capturedAtISO8601: String,
        locationData: Data?,
        idempotencyKey: String,
        retryCount: Int = 0,
        lastError: String? = nil,
        createdAt: Date = .now,
    ) {
        self.id = id
        self.accountID = accountID
        self.jobID = jobID
        self.subtaskID = subtaskID
        self.localPath = localPath
        self.capturedAtISO8601 = capturedAtISO8601
        self.locationData = locationData
        self.idempotencyKey = idempotencyKey
        self.retryCount = retryCount
        self.lastError = lastError
        self.createdAt = createdAt
    }
}

@Model
final class PendingClientJobRecord {
    @Attribute(.unique) var storageID: String
    var accountID: String
    var requestData: Data
    var updatedAt: Date

    init(storageID: String, accountID: String, requestData: Data, updatedAt: Date = .now) {
        self.storageID = storageID
        self.accountID = accountID
        self.requestData = requestData
        self.updatedAt = updatedAt
    }
}

@Model
final class InboxRecord {
    @Attribute(.unique) var storageID: String
    var accountID: String
    var eventID: String
    var cursor: String
    var topic: String
    var title: String
    var body: String
    var data: Data
    var readAt: Date?
    var createdAt: Date

    init(
        storageID: String,
        accountID: String,
        eventID: String,
        cursor: String,
        topic: String,
        title: String,
        body: String,
        data: Data,
        readAt: Date?,
        createdAt: Date,
    ) {
        self.storageID = storageID
        self.accountID = accountID
        self.eventID = eventID
        self.cursor = cursor
        self.topic = topic
        self.title = title
        self.body = body
        self.data = data
        self.readAt = readAt
        self.createdAt = createdAt
    }
}

struct PendingEvidence: Identifiable, Sendable {
    let id: String
    let accountID: String
    let jobID: String
    let subtaskID: String
    let localFileURL: URL
    let capturedAtISO8601: String
    let location: Point?
    let idempotencyKey: String
    let retryCount: Int
    let lastError: String?
}

struct InboxItem: Identifiable, Equatable, Sendable {
    let id: String
    let eventID: String
    let cursor: String
    let topic: String
    let title: String
    let body: String
    let data: [String: JSONValue]
    let readAt: Date?
    let createdAt: Date
}

@MainActor
final class LocalStore: ObservableObject {
    @Published private(set) var revision = UUID()

    private let container: ModelContainer
    private let context: ModelContext
    private let fileManager: FileManager
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    private init(isStoredInMemoryOnly: Bool, fileManager: FileManager = .default) throws {
        let schema = Schema([
            CachedPayloadRecord.self,
            PendingEvidenceRecord.self,
            PendingClientJobRecord.self,
            InboxRecord.self,
        ])
        let configuration = ModelConfiguration(
            "NetworkPeerCache",
            schema: schema,
            isStoredInMemoryOnly: isStoredInMemoryOnly,
        )
        container = try ModelContainer(for: schema, configurations: [configuration])
        context = ModelContext(container)
        self.fileManager = fileManager
    }

    static func make() -> LocalStore {
        if let store = try? LocalStore(isStoredInMemoryOnly: false) {
            return store
        }
        // A full local disk should not prevent a signed-in worker from using the API.
        return try! LocalStore(isStoredInMemoryOnly: true)
    }

    func cached<Value: Decodable>(_ type: Value.Type, key: String) -> Value? {
        guard let record = cachedRecord(key), record.payload.isEmpty == false else { return nil }
        return try? decoder.decode(Value.self, from: record.payload)
    }

    func cache<Value: Encodable>(_ value: Value, key: String) {
        guard let payload = try? encoder.encode(value) else { return }
        if let existing = cachedRecord(key) {
            existing.payload = payload
            existing.updatedAt = .now
        } else {
            context.insert(CachedPayloadRecord(key: key, payload: payload))
        }
        saveChanges()
    }

    func syncCursor(accountID: String) -> String {
        cached(String.self, key: cursorKey(accountID: accountID)) ?? "0"
    }

    func workerSyncCursor(accountID: String) -> String {
        cached(String.self, key: workerCursorKey(accountID: accountID)) ?? "0"
    }

    func applySyncPage(_ page: SyncPage, accountID: String) {
        for event in page.events {
            applyInboxProjection(for: event, accountID: accountID)
        }
        storeCursor(page.nextCursor, key: cursorKey(accountID: accountID))
        saveChanges()
    }

    func applyWorkerSyncPage(_ page: WorkerSyncPage, accountID: String) {
        for event in page.events {
            applyInboxProjection(for: event, accountID: accountID)
        }
        storeCursor(page.nextCursor, key: workerCursorKey(accountID: accountID))
        saveChanges()
    }

    func resetSyncedData(accountID: String) {
        let prefix = accountPrefix(accountID)
        for record in all(CachedPayloadRecord.self) where record.key.hasPrefix(prefix) && !record.key.contains(".evidence.") {
            context.delete(record)
        }
        for record in all(InboxRecord.self) where record.accountID == accountID {
            context.delete(record)
        }
        saveChanges()
    }

    func inbox(accountID: String) -> [InboxItem] {
        all(InboxRecord.self)
            .filter { $0.accountID == accountID }
            .map { record in
                InboxItem(
                    id: record.storageID,
                    eventID: record.eventID,
                    cursor: record.cursor,
                    topic: record.topic,
                    title: record.title,
                    body: record.body,
                    data: (try? decoder.decode([String: JSONValue].self, from: record.data)) ?? [:],
                    readAt: record.readAt,
                    createdAt: record.createdAt,
                )
            }
            .sorted { $0.createdAt > $1.createdAt }
    }

    func markInboxRead(id: String) {
        guard let record = all(InboxRecord.self).first(where: { $0.storageID == id }) else { return }
        record.readAt = .now
        saveChanges()
    }

    func markAllInboxRead(accountID: String) {
        var changed = false
        for record in all(InboxRecord.self) where record.accountID == accountID && record.readAt == nil {
            record.readAt = .now
            changed = true
        }
        if changed { saveChanges() }
    }

    func unreadInboxCount(accountID: String) -> Int {
        all(InboxRecord.self).filter { $0.accountID == accountID && $0.readAt == nil }.count
    }

    func applyNotifications(_ page: NotificationPage, accountID: String, replacing: Bool) {
        if replacing {
            let incomingIDs = Set(page.items.map(\.id))
            for record in all(InboxRecord.self) where record.accountID == accountID && !incomingIDs.contains(record.eventID) {
                context.delete(record)
            }
        }
        for notification in page.items {
            upsertInboxRecord(notification, accountID: accountID)
        }
        saveChanges()
    }

    func applyNotification(_ notification: NetworkPeerNotification, accountID: String) {
        upsertInboxRecord(notification, accountID: accountID)
        saveChanges()
    }

    func enqueueEvidence(
        accountID: String,
        jobID: String,
        subtaskID: String,
        sourceURL: URL,
        capturedAtISO8601: String,
        location: Point?,
        idempotencyKey: String,
    ) throws -> PendingEvidence {
        let id = UUID().uuidString
        let directory = try evidenceDirectory()
        let extensionName = sourceURL.pathExtension.isEmpty ? "bin" : sourceURL.pathExtension
        let destination = directory.appendingPathComponent(id).appendingPathExtension(extensionName)
        do {
            try fileManager.copyItem(at: sourceURL, to: destination)
            #if os(iOS)
            // Retries happen from foreground UI, so queued evidence can remain unreadable while locked.
            try fileManager.setAttributes(
                [.protectionKey: FileProtectionType.complete],
                ofItemAtPath: destination.path,
            )
            #endif
        } catch {
            try? fileManager.removeItem(at: destination)
            throw error
        }

        let record = PendingEvidenceRecord(
            id: id,
            accountID: accountID,
            jobID: jobID,
            subtaskID: subtaskID,
            localPath: destination.path,
            capturedAtISO8601: capturedAtISO8601,
            locationData: location.flatMap { try? encoder.encode($0) },
            idempotencyKey: idempotencyKey,
        )
        context.insert(record)
        do {
            try context.save()
            revision = UUID()
        } catch {
            context.delete(record)
            try? fileManager.removeItem(at: destination)
            throw error
        }
        return pendingEvidence(from: record)
    }

    func pendingEvidence(accountID: String, jobID: String? = nil) -> [PendingEvidence] {
        let records = all(PendingEvidenceRecord.self)
            .filter { record in
                record.accountID == accountID && (jobID == nil || record.jobID == jobID)
            }
            .sorted { $0.createdAt < $1.createdAt }
        #if os(iOS)
        for record in records {
            try? fileManager.setAttributes(
                [.protectionKey: FileProtectionType.complete],
                ofItemAtPath: record.localPath,
            )
        }
        #endif
        return records.map(pendingEvidence)
    }

    func pendingClientJob(accountID: String) -> CreateJobRequest? {
        guard let record = all(PendingClientJobRecord.self).first(where: { $0.accountID == accountID }) else {
            return nil
        }
        return try? decoder.decode(CreateJobRequest.self, from: record.requestData)
    }

    func savePendingClientJob(_ request: CreateJobRequest, accountID: String) {
        guard let data = try? encoder.encode(request) else { return }
        let storageID = "\(accountID).pending-client-job"
        if let record = all(PendingClientJobRecord.self).first(where: { $0.storageID == storageID }) {
            record.requestData = data
            record.updatedAt = .now
        } else {
            context.insert(PendingClientJobRecord(storageID: storageID, accountID: accountID, requestData: data))
        }
        saveChanges()
    }

    func discardPendingClientJob(accountID: String) {
        for record in all(PendingClientJobRecord.self) where record.accountID == accountID {
            context.delete(record)
        }
        saveChanges()
    }

    func markEvidenceFailure(id: String, error: Error) {
        guard let record = all(PendingEvidenceRecord.self).first(where: { $0.id == id }) else { return }
        record.retryCount += 1
        record.lastError = error.localizedDescription
        saveChanges()
    }

    func removeEvidence(id: String) {
        guard let record = all(PendingEvidenceRecord.self).first(where: { $0.id == id }) else { return }
        try? fileManager.removeItem(atPath: record.localPath)
        context.delete(record)
        saveChanges()
    }

    func cacheKey(accountID: String, name: String) -> String {
        "\(accountPrefix(accountID))\(name)"
    }

    private func cachedRecord(_ key: String) -> CachedPayloadRecord? {
        all(CachedPayloadRecord.self).first { $0.key == key }
    }

    private func all<Model: PersistentModel>(_ type: Model.Type) -> [Model] {
        (try? context.fetch(FetchDescriptor<Model>())) ?? []
    }

    private func accountPrefix(_ accountID: String) -> String {
        "account.\(accountID)."
    }

    private func cursorKey(accountID: String) -> String {
        cacheKey(accountID: accountID, name: "sync.cursor")
    }

    private func workerCursorKey(accountID: String) -> String {
        cacheKey(accountID: accountID, name: "worker.sync.cursor")
    }

    private func storeCursor(_ cursor: String, key: String) {
        guard let payload = try? encoder.encode(cursor) else { return }
        if let existing = cachedRecord(key) {
            existing.payload = payload
            existing.updatedAt = .now
        } else {
            context.insert(CachedPayloadRecord(key: key, payload: payload))
        }
    }

    private func pendingEvidence(from record: PendingEvidenceRecord) -> PendingEvidence {
        PendingEvidence(
            id: record.id,
            accountID: record.accountID,
            jobID: record.jobID,
            subtaskID: record.subtaskID,
            localFileURL: URL(fileURLWithPath: record.localPath),
            capturedAtISO8601: record.capturedAtISO8601,
            location: record.locationData.flatMap { try? decoder.decode(Point.self, from: $0) },
            idempotencyKey: record.idempotencyKey,
            retryCount: record.retryCount,
            lastError: record.lastError,
        )
    }

    private func evidenceDirectory() throws -> URL {
        let base = try fileManager.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true,
        )
        let directory = base.appendingPathComponent("NetworkPeer/Evidence", isDirectory: true)
        try fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
        #if os(iOS)
        try fileManager.setAttributes(
            [.protectionKey: FileProtectionType.complete],
            ofItemAtPath: directory.path,
        )
        #endif
        return directory
    }

    private func applyInboxProjection(for event: SyncEvent, accountID: String) {
        if event.topic == "NOTIFICATION_READ", let entityID = event.entityID {
            let target = all(InboxRecord.self).first {
                $0.accountID == accountID && ($0.eventID == entityID || $0.storageID == "\(accountID).\(entityID)")
            }
            target?.readAt = .now
            return
        }

        let projection = event.notification
        let eventID = projection?.id ?? event.eventID
        let storageID = "\(accountID).\(eventID)"
        let title = projection?.title ?? event.payload["title"]?.stringValue ?? readableTopic(event.topic)
        let body = projection?.body
            ?? event.payload["body"]?.stringValue
            ?? event.payload["message"]?.stringValue
            ?? "A NetworkPeer update is ready."
        let data = event.payload
        let readAt = parseDate(projection?.readAt)
        let createdAt = parseDate(event.createdAt) ?? .now
        let encodedData = (try? encoder.encode(data)) ?? Data("{}".utf8)

        if let existing = all(InboxRecord.self).first(where: { $0.storageID == storageID }) {
            existing.cursor = event.cursor
            existing.topic = event.topic
            existing.title = title
            existing.body = body
            existing.data = encodedData
            existing.readAt = existing.readAt ?? readAt
            existing.createdAt = createdAt
        } else {
            context.insert(InboxRecord(
                storageID: storageID,
                accountID: accountID,
                eventID: eventID,
                cursor: event.cursor,
                topic: event.topic,
                title: title,
                body: body,
                data: encodedData,
                readAt: readAt,
                createdAt: createdAt,
            ))
        }
    }

    private func upsertInboxRecord(_ notification: NetworkPeerNotification, accountID: String) {
        let storageID = "\(accountID).\(notification.id)"
        let encodedData = (try? encoder.encode(notification.data)) ?? Data("{}".utf8)
        let remoteReadAt = parseDate(notification.readAt)
        let createdAt = parseDate(notification.createdAt) ?? .now

        if let existing = all(InboxRecord.self).first(where: { $0.storageID == storageID }) {
            existing.cursor = notification.cursor
            existing.topic = notification.topic
            existing.title = notification.title
            existing.body = notification.body
            existing.data = encodedData
            existing.readAt = remoteReadAt ?? existing.readAt
            existing.createdAt = createdAt
        } else {
            context.insert(InboxRecord(
                storageID: storageID,
                accountID: accountID,
                eventID: notification.id,
                cursor: notification.cursor,
                topic: notification.topic,
                title: notification.title,
                body: notification.body,
                data: encodedData,
                readAt: remoteReadAt,
                createdAt: createdAt,
            ))
        }
    }

    private func readableTopic(_ topic: String) -> String {
        topic.replacingOccurrences(of: "_", with: " ").capitalized
    }

    private func parseDate(_ value: String?) -> Date? {
        guard let value else { return nil }
        let formatter = ISO8601DateFormatter()
        if let date = formatter.date(from: value) { return date }
        formatter.formatOptions.insert(.withFractionalSeconds)
        return formatter.date(from: value)
    }

    private func saveChanges() {
        guard context.hasChanges else { return }
        do {
            try context.save()
            revision = UUID()
        } catch {
            // Local cache failures are non-fatal; authenticated server state remains authoritative.
        }
    }
}
