import Foundation

/// The server sends these fields in the top-level FCM data payload, alongside
/// APNs `aps`; nested or alert-style payloads are intentionally not accepted.
public struct DataOnlyPushPayload: Equatable, Sendable {
    public let recipientUserID: String
    public let title: String
    public let body: String
    public let cursor: String
    public let topic: String
    public let jobID: String?

    public init?(userInfo: [AnyHashable: Any]) {
        guard !Self.containsAlert(in: userInfo[AnyHashable("aps")]) else { return nil }
        guard let recipientUserID = Self.requiredString("recipient_user_id", in: userInfo),
              let title = Self.requiredString("title", in: userInfo),
              let body = Self.requiredString("body", in: userInfo),
              let cursor = Self.requiredString("cursor", in: userInfo),
              let topic = Self.requiredString("topic", in: userInfo),
              Self.isValidCursor(cursor) else {
            return nil
        }

        let jobID: String?
        if userInfo[AnyHashable("job_id")] != nil {
            guard let value = Self.requiredString("job_id", in: userInfo) else { return nil }
            jobID = value
        } else {
            jobID = nil
        }

        self.recipientUserID = recipientUserID
        self.title = title
        self.body = body
        self.cursor = cursor
        self.topic = topic
        self.jobID = jobID
    }

    public func isForActiveSession(_ session: StoredSession?) -> Bool {
        session?.user.id == recipientUserID
    }

    public var userInfo: [AnyHashable: Any] {
        var values: [AnyHashable: Any] = [
            "recipient_user_id": recipientUserID,
            "title": title,
            "body": body,
            "cursor": cursor,
            "topic": topic,
        ]
        if let jobID {
            values["job_id"] = jobID
        }
        return values
    }

    private static func requiredString(_ key: String, in userInfo: [AnyHashable: Any]) -> String? {
        guard let value = userInfo[AnyHashable(key)] as? String else { return nil }
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }

    private static func containsAlert(in aps: Any?) -> Bool {
        if let aps = aps as? [AnyHashable: Any] {
            return aps[AnyHashable("alert")] != nil
        }
        if let aps = aps as? [String: Any] {
            return aps["alert"] != nil
        }
        if let aps = aps as? NSDictionary {
            return aps.object(forKey: "alert") != nil
        }
        return false
    }

    private static func isValidCursor(_ value: String) -> Bool {
        let maximumCursor = "9223372036854775807"
        guard value.unicodeScalars.allSatisfy({ (48 ... 57).contains($0.value) }),
              value.count <= maximumCursor.count else {
            return false
        }
        return value.count < maximumCursor.count || value <= maximumCursor
    }
}
