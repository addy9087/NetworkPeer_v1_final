package com.networkpeer.mobile.core.evidence

import android.content.Context
import android.net.Uri
import com.networkpeer.mobile.core.data.DurableAppState
import com.networkpeer.mobile.core.data.PendingEvidenceUpload
import com.networkpeer.mobile.core.model.EvidenceSummary
import com.networkpeer.mobile.core.model.Point
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.UUID

/**
 * Persists each evidence request before networking. Retrying reuses its immutable metadata and
 * idempotency key, which is required by the upload contract.
 */
class DurableEvidenceUploadQueue(
    private val context: Context,
    private val uploader: EvidenceUploader,
    private val state: DurableAppState,
) {
    private val uploadMutex = Mutex()

    suspend fun enqueueAndUpload(
        jobId: String,
        subtaskId: String,
        uri: Uri,
        capturedAt: Instant = Instant.now(),
        location: Point? = null,
        appOwnedUri: Boolean = false,
    ): EvidenceSummary? {
        val uploadMetadata = try {
            uploader.inspect(uri)
        } catch (failure: Throwable) {
            if (appOwnedUri) EvidenceCapture.delete(context, uri)
            throw failure
        }
        val item = PendingEvidenceUpload(
            id = UUID.randomUUID().toString(),
            jobId = jobId,
            subtaskId = subtaskId,
            uri = uri.toString(),
            capturedAt = capturedAt.toString(),
            location = location,
            idempotencyKey = UUID.randomUUID().toString(),
            uploadMetadata = uploadMetadata,
            appOwnedUri = appOwnedUri,
        )
        state.enqueueEvidence(item)
        return retry(item.id)
    }

    suspend fun retry(id: String): EvidenceSummary? = uploadMutex.withLock {
        val item = state.pendingEvidence.value.firstOrNull { it.id == id } ?: return@withLock null
        try {
            val result = uploader.upload(
                jobId = item.jobId,
                subtaskId = item.subtaskId,
                uri = Uri.parse(item.uri),
                capturedAt = Instant.parse(item.capturedAt),
                location = item.location,
                idempotencyKey = item.idempotencyKey,
                expectedMetadata = item.uploadMetadata,
            )
            state.recordConfirmedEvidence(result.evidence)
            state.removePendingEvidence(item.id)
            if (item.appOwnedUri) EvidenceCapture.delete(context, Uri.parse(item.uri))
            result.evidence
        } catch (failure: Throwable) {
            state.markEvidenceAttempt(item.id, failure.message ?: "Evidence upload needs to be retried.")
            throw failure
        }
    }

    suspend fun retryForJob(jobId: String) {
        state.pendingEvidence.value
            .filter { it.jobId == jobId }
            .forEach { item ->
                try {
                    retry(item.id)
                } catch (_: Throwable) {
                    // The queue retains the failure and lets the worker explicitly retry it later.
                }
            }
    }
}
