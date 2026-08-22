import AVFoundation
import CryptoKit
import Foundation

public struct EvidenceUploadResult: Sendable {
    public let evidence: EvidenceSummary
    public let wasAlreadyUploaded: Bool
}

/**
 * S3 is only reached through an API-issued, short-lived POST policy. This code
 * intentionally has no AWS SDK dependency and no access to S3 credentials.
 */
public final class EvidenceUploader: @unchecked Sendable {
    private let api: NetworkPeerAPI
    private let urlSession: URLSession
    private let fileManager: FileManager
    private let maximumEvidenceBytes: Int64

    public init(
        api: NetworkPeerAPI,
        urlSession: URLSession = .shared,
        fileManager: FileManager = .default,
        maximumEvidenceBytes: Int64 = EvidenceUploadLimits.defaultMaximumFileSizeBytes,
    ) {
        self.api = api
        self.urlSession = urlSession
        self.fileManager = fileManager
        self.maximumEvidenceBytes = EvidenceUploadLimits.isValid(maximumEvidenceBytes)
            ? maximumEvidenceBytes
            : EvidenceUploadLimits.defaultMaximumFileSizeBytes
    }

    public func upload(
        jobID: String,
        subtaskID: String,
        fileURL: URL,
        capturedAt: Date = .now,
        location: Point? = nil,
        idempotencyKey: String = UUID().uuidString,
    ) async throws -> EvidenceUploadResult {
        let formatter = ISO8601DateFormatter()
        return try await upload(
            jobID: jobID,
            subtaskID: subtaskID,
            fileURL: fileURL,
            capturedAtISO8601: formatter.string(from: capturedAt),
            location: location,
            idempotencyKey: idempotencyKey,
        )
    }

    /// Retries use the original timestamp and idempotency key so the API can
    /// safely return the prior reservation instead of creating another record.
    public func upload(
        jobID: String,
        subtaskID: String,
        fileURL: URL,
        capturedAtISO8601: String,
        location: Point? = nil,
        idempotencyKey: String,
    ) async throws -> EvidenceUploadResult {
        let file = try await inspect(fileURL)
        let reservation = try await api.reserveEvidence(
            ReserveEvidenceRequest(
                jobID: jobID,
                subtaskID: subtaskID,
                mediaType: file.mediaType,
                mimeType: file.mimeType,
                fileSizeBytes: file.size,
                capturedAt: capturedAtISO8601,
                checksumSHA256: file.sha256Hex,
                idempotencyKey: idempotencyKey,
                location: location,
            ),
        )
        if let target = reservation.upload {
            try await post(fileURL: fileURL, mimeType: file.mimeType, target: target)
        }
        let evidence = try await api.confirmEvidence(mediaID: reservation.evidence.id)
        return EvidenceUploadResult(evidence: evidence, wasAlreadyUploaded: reservation.upload == nil)
    }

    private func inspect(_ fileURL: URL) async throws -> LocalEvidence {
        guard fileURL.isFileURL else { throw NetworkPeerAPIError.unreadableEvidence }
        let mimeType = try await mimeType(for: fileURL)
        guard let mediaType = mediaType(for: mimeType) else { throw NetworkPeerAPIError.unsupportedMedia }
        let digest = try sha256(of: fileURL)
        guard digest.size <= maximumEvidenceBytes else { throw NetworkPeerAPIError.evidenceTooLarge }
        return LocalEvidence(mimeType: mimeType, mediaType: mediaType, size: digest.size, sha256Hex: digest.hex)
    }

    private func mimeType(for fileURL: URL) async throws -> String {
        let extensionName = fileURL.pathExtension.lowercased()
        if extensionName == "webm" {
            return try await webMMimeType(for: fileURL)
        }
        let mapping: [String: String] = [
            "jpg": "image/jpeg", "jpeg": "image/jpeg", "png": "image/png", "webp": "image/webp",
            "mp4": "video/mp4", "mov": "video/quicktime",
            "mp3": "audio/mpeg", "m4a": "audio/mp4", "wav": "audio/wav", "pdf": "application/pdf",
        ]
        guard let value = mapping[extensionName] else { throw NetworkPeerAPIError.unsupportedMedia }
        return value
    }

    private func webMMimeType(for fileURL: URL) async throws -> String {
        let asset = AVURLAsset(url: fileURL)
        do {
            let videoTracks = try await asset.loadTracks(withMediaType: .video)
            if !videoTracks.isEmpty {
                return "video/webm"
            }
            let audioTracks = try await asset.loadTracks(withMediaType: .audio)
            if !audioTracks.isEmpty {
                return "audio/webm"
            }
        } catch {
            throw NetworkPeerAPIError.unsupportedMedia
        }
        throw NetworkPeerAPIError.unsupportedMedia
    }

    private func mediaType(for mimeType: String) -> MediaType? {
        switch mimeType {
        case "image/jpeg", "image/png", "image/webp": return .image
        case "video/mp4", "video/quicktime", "video/webm": return .video
        case "audio/mpeg", "audio/mp4", "audio/wav", "audio/webm": return .audio
        case "application/pdf": return .document
        default: return nil
        }
    }

    private func sha256(of fileURL: URL) throws -> (size: Int64, hex: String) {
        let handle: FileHandle
        do {
            handle = try FileHandle(forReadingFrom: fileURL)
        } catch {
            throw NetworkPeerAPIError.unreadableEvidence
        }
        defer { try? handle.close() }
        var hasher = SHA256()
        var size: Int64 = 0
        while true {
            let data = try handle.read(upToCount: 64 * 1024) ?? Data()
            if data.isEmpty { break }
            hasher.update(data: data)
            size += Int64(data.count)
            if size > maximumEvidenceBytes { break }
        }
        return (size, hasher.finalize().map { String(format: "%02x", $0) }.joined())
    }

    private func post(fileURL: URL, mimeType: String, target: EvidenceUploadTarget) async throws {
        guard target.url.scheme == "https" else {
            throw NetworkPeerAPIError.invalidConfiguration("Evidence uploads require an HTTPS URL issued by NetworkPeer.")
        }
        let boundary = "NetworkPeer-\(UUID().uuidString)"
        let multipartURL = try makeMultipartFile(
            fields: target.fields,
            fileURL: fileURL,
            mimeType: mimeType,
            boundary: boundary,
        )
        defer { try? fileManager.removeItem(at: multipartURL) }

        var request = URLRequest(url: target.url)
        request.httpMethod = "POST"
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        let response: URLResponse
        do {
            (_, response) = try await urlSession.upload(for: request, fromFile: multipartURL)
        } catch {
            throw NetworkPeerAPIError.transport("Evidence upload could not reach the temporary upload endpoint. Keep the file and retry.")
        }
        guard let http = response as? HTTPURLResponse, (200 ..< 300).contains(http.statusCode) else {
            let status = (response as? HTTPURLResponse)?.statusCode ?? 0
            throw NetworkPeerAPIError.server(code: "EVIDENCE_UPLOAD_FAILED", message: "S3 rejected the evidence upload.", statusCode: status)
        }
    }

    private func makeMultipartFile(
        fields: [String: String],
        fileURL: URL,
        mimeType: String,
        boundary: String,
    ) throws -> URL {
        let temporaryURL = fileManager.temporaryDirectory.appendingPathComponent("networkpeer-evidence-\(UUID().uuidString)")
        guard fileManager.createFile(atPath: temporaryURL.path, contents: nil) else {
            throw NetworkPeerAPIError.unreadableEvidence
        }
        let output = try FileHandle(forWritingTo: temporaryURL)
        defer { try? output.close() }
        func write(_ value: String) throws {
            guard let data = value.data(using: .utf8) else { throw NetworkPeerAPIError.unreadableEvidence }
            try output.write(contentsOf: data)
        }
        for (name, value) in fields {
            try write("--\(boundary)\r\n")
            try write("Content-Disposition: form-data; name=\"\(name)\"\r\n\r\n")
            try write("\(value)\r\n")
        }
        try write("--\(boundary)\r\n")
        try write("Content-Disposition: form-data; name=\"file\"; filename=\"evidence\"\r\n")
        try write("Content-Type: \(mimeType)\r\n\r\n")
        let input = try FileHandle(forReadingFrom: fileURL)
        defer { try? input.close() }
        while true {
            let data = try input.read(upToCount: 64 * 1024) ?? Data()
            if data.isEmpty { break }
            try output.write(contentsOf: data)
        }
        try write("\r\n--\(boundary)--\r\n")
        return temporaryURL
    }

    private struct LocalEvidence {
        let mimeType: String
        let mediaType: MediaType
        let size: Int64
        let sha256Hex: String
    }
}
