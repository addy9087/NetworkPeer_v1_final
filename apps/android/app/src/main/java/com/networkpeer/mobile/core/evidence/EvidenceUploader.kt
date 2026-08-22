package com.networkpeer.mobile.core.evidence

import android.content.ContentResolver
import android.net.Uri
import com.networkpeer.mobile.core.data.MarketplaceRepository
import com.networkpeer.mobile.core.model.EvidenceSummary
import com.networkpeer.mobile.core.model.MediaType
import com.networkpeer.mobile.core.model.NetworkPeerApiException
import com.networkpeer.mobile.core.model.Point
import com.networkpeer.mobile.core.network.ReserveEvidenceBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

data class EvidenceUploadResult(
    val evidence: EvidenceSummary,
    val wasAlreadyUploaded: Boolean,
)

@Serializable
data class EvidenceUploadMetadata(
    val mediaType: MediaType,
    val mimeType: String,
    val fileSizeBytes: Long,
    val checksumSha256: String,
)

/**
 * Uploads only through an API-issued S3 POST target. The phone never receives
 * AWS credentials or chooses an object key.
 */
class EvidenceUploader(
    private val contentResolver: ContentResolver,
    private val repository: MarketplaceRepository,
    private val uploadClient: OkHttpClient,
) {
    suspend fun upload(
        jobId: String,
        subtaskId: String,
        uri: Uri,
        capturedAt: Instant = Instant.now(),
        location: Point? = null,
        idempotencyKey: String = UUID.randomUUID().toString(),
        expectedMetadata: EvidenceUploadMetadata? = null,
    ): EvidenceUploadResult = withContext(Dispatchers.IO) {
        val metadata = inspect(uri)
        if (expectedMetadata != null && metadata != expectedMetadata) {
            throw NetworkPeerApiException(
                "EVIDENCE_CHANGED",
                "Evidence changed after its upload request was queued. Select the file again to create a new secure upload.",
            )
        }

        val reservation = repository.reserveEvidence(
            ReserveEvidenceBody(
                job_id = jobId,
                subtask_id = subtaskId,
                media_type = metadata.mediaType.name,
                mime_type = metadata.mimeType,
                file_size_bytes = metadata.fileSizeBytes,
                captured_at = capturedAt.toString(),
                checksum_sha256 = metadata.checksumSha256,
                idempotency_key = idempotencyKey,
                location = location,
            ),
        )
        val target = reservation.upload
        if (target != null) uploadToPresignedPost(target.url, target.fields, uri, metadata.mimeType, metadata.fileSizeBytes)
        val confirmed = repository.confirmEvidence(reservation.evidence.id)
        EvidenceUploadResult(confirmed, wasAlreadyUploaded = target == null)
    }

    suspend fun inspect(uri: Uri): EvidenceUploadMetadata = withContext(Dispatchers.IO) {
        val mimeType = contentResolver.getType(uri)?.lowercase()
            ?: throw NetworkPeerApiException("MEDIA_TYPE_UNKNOWN", "The selected file has no recognised MIME type.")
        val mediaType = mediaTypeFor(mimeType)
            ?: throw NetworkPeerApiException("MEDIA_TYPE_NOT_ALLOWED", "Select a JPEG, PNG, WebP, supported video/audio file, or PDF.")
        val digest = digest(uri)
        if (digest.sizeBytes > MAX_EVIDENCE_BYTES) {
            throw NetworkPeerApiException("MEDIA_TOO_LARGE", "Evidence exceeds the 25 MiB platform limit.")
        }
        EvidenceUploadMetadata(
            mediaType = mediaType,
            mimeType = mimeType,
            fileSizeBytes = digest.sizeBytes,
            checksumSha256 = digest.sha256Hex,
        )
    }

    private fun uploadToPresignedPost(
        url: String,
        fields: Map<String, String>,
        uri: Uri,
        mimeType: String,
        sizeBytes: Long,
    ) {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM).apply {
            fields.forEach { (name, value) -> addFormDataPart(name, value) }
            addFormDataPart(
                "file",
                "evidence",
                ContentUriRequestBody(contentResolver, uri, mimeType, sizeBytes),
            )
        }.build()
        val request = Request.Builder().url(url).post(body).build()
        uploadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw NetworkPeerApiException(
                    "EVIDENCE_UPLOAD_FAILED",
                    "S3 rejected the evidence upload (${response.code}).",
                    response.code,
                )
            }
        }
    }

    private fun digest(uri: Uri): LocalDigest {
        val hasher = MessageDigest.getInstance("SHA-256")
        var size = 0L
        contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                hasher.update(buffer, 0, count)
                size += count
                if (size > MAX_EVIDENCE_BYTES) break
            }
        } ?: throw NetworkPeerApiException("EVIDENCE_UNREADABLE", "The selected evidence file could not be read.")
        return LocalDigest(size, hasher.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) })
    }

    private fun mediaTypeFor(mimeType: String): MediaType? = when (mimeType) {
        "image/jpeg", "image/png", "image/webp" -> MediaType.IMAGE
        "video/mp4", "video/quicktime", "video/webm" -> MediaType.VIDEO
        "audio/mpeg", "audio/mp4", "audio/wav", "audio/webm" -> MediaType.AUDIO
        "application/pdf" -> MediaType.DOCUMENT
        else -> null
    }

    private data class LocalDigest(val sizeBytes: Long, val sha256Hex: String)

    private class ContentUriRequestBody(
        private val resolver: ContentResolver,
        private val uri: Uri,
        private val mimeType: String,
        private val sizeBytes: Long,
    ) : RequestBody() {
        override fun contentType() = mimeType.toMediaType()

        override fun contentLength() = sizeBytes

        override fun writeTo(sink: BufferedSink) {
            val source = resolver.openInputStream(uri)?.source()
                ?: throw NetworkPeerApiException("EVIDENCE_UNREADABLE", "The selected evidence file could not be read.")
            source.use { sink.writeAll(it) }
        }
    }

    private companion object {
        const val MAX_EVIDENCE_BYTES = 25L * 1024L * 1024L
    }
}
