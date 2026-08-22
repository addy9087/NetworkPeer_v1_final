import Foundation
import Security

public protocol SessionStoring: Sendable {
    func read() -> StoredSession?
    func save(_ session: StoredSession) throws
    func clear() throws
    @discardableResult
    func replace(_ session: StoredSession, ifMatches existing: StoredSession) throws -> Bool
    @discardableResult
    func clear(ifMatches session: StoredSession) throws -> Bool
}

public extension SessionStoring {
    @discardableResult
    func replace(_ session: StoredSession, ifMatches existing: StoredSession) throws -> Bool {
        guard read() == existing else { return false }
        try save(session)
        return true
    }

    @discardableResult
    func clear(ifMatches session: StoredSession) throws -> Bool {
        guard read() == session else { return false }
        try clear()
        return true
    }
}

/**
 * Keychain persistence is intentionally limited to the NetworkPeer token pair.
 * The app does not store AWS credentials, database URLs, or payment secrets.
 */
public final class KeychainSessionStore: SessionStoring, @unchecked Sendable {
    private let service: String
    private let account = "networkpeer.session"
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()
    private let lock = NSLock()

    public init(service: String = Bundle.main.bundleIdentifier ?? "com.networkpeer.mobile") {
        self.service = service
    }

    public func read() -> StoredSession? {
        lock.lock()
        defer { lock.unlock() }
        return readLocked()
    }

    public func save(_ session: StoredSession) throws {
        lock.lock()
        defer { lock.unlock() }
        let data = try encoder.encode(session)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        let update: [String: Any] = [kSecValueData as String: data]
        let updateResult = SecItemUpdate(query as CFDictionary, update as CFDictionary)
        if updateResult == errSecItemNotFound {
            var add = query
            add[kSecValueData as String] = data
            add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            let addResult = SecItemAdd(add as CFDictionary, nil)
            guard addResult == errSecSuccess else { throw KeychainError.unexpectedStatus(addResult) }
        } else if updateResult != errSecSuccess {
            throw KeychainError.unexpectedStatus(updateResult)
        }
    }

    public func replace(_ session: StoredSession, ifMatches existing: StoredSession) throws -> Bool {
        lock.lock()
        defer { lock.unlock() }
        let data = try encoder.encode(session)
        guard readLocked() == existing else { return false }
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        let update = [kSecValueData as String: data]
        let result = SecItemUpdate(query as CFDictionary, update as CFDictionary)
        if result == errSecItemNotFound { return false }
        guard result == errSecSuccess else { throw KeychainError.unexpectedStatus(result) }
        return true
    }

    public func clear() throws {
        lock.lock()
        defer { lock.unlock() }
        try clearLocked()
    }

    public func clear(ifMatches session: StoredSession) throws -> Bool {
        lock.lock()
        defer { lock.unlock() }
        guard readLocked() == session else { return false }
        try clearLocked()
        return true
    }

    private func readLocked() -> StoredSession? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var item: CFTypeRef?
        let result = SecItemCopyMatching(query as CFDictionary, &item)
        guard result == errSecSuccess, let data = item as? Data else { return nil }
        return try? decoder.decode(StoredSession.self, from: data)
    }

    private func clearLocked() throws {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        let result = SecItemDelete(query as CFDictionary)
        guard result == errSecSuccess || result == errSecItemNotFound else {
            throw KeychainError.unexpectedStatus(result)
        }
    }
}

public final class InMemorySessionStore: SessionStoring, @unchecked Sendable {
    private let lock = NSLock()
    private var value: StoredSession?

    public init(_ value: StoredSession? = nil) {
        self.value = value
    }

    public func read() -> StoredSession? {
        lock.lock()
        defer { lock.unlock() }
        return value
    }

    public func save(_ session: StoredSession) throws {
        lock.lock()
        defer { lock.unlock() }
        value = session
    }

    public func clear() throws {
        lock.lock()
        defer { lock.unlock() }
        value = nil
    }

    public func replace(_ session: StoredSession, ifMatches existing: StoredSession) throws -> Bool {
        lock.lock()
        defer { lock.unlock() }
        guard value == existing else { return false }
        value = session
        return true
    }

    public func clear(ifMatches session: StoredSession) throws -> Bool {
        lock.lock()
        defer { lock.unlock() }
        guard value == session else { return false }
        value = nil
        return true
    }
}

public enum KeychainError: Error, Sendable {
    case unexpectedStatus(OSStatus)
}
