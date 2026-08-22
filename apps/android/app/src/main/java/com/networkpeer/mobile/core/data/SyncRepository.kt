package com.networkpeer.mobile.core.data

import com.networkpeer.mobile.core.model.NetworkPeerApiException
import com.networkpeer.mobile.core.model.SyncEvent
import com.networkpeer.mobile.core.model.UserRole
import com.networkpeer.mobile.core.network.SecureSessionStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.CancellationException

data class SyncReconciliation(
    val events: List<SyncEvent>,
    val reachedEnd: Boolean,
)

/** Replays every available page from the durable cursor before UI data is refreshed. */
class SyncRepository(
    private val marketplaceRepository: MarketplaceRepository,
    private val state: DurableAppState,
    private val sessionStore: SecureSessionStore,
) {
    private val reconciliationMutex = Mutex()

    /**
     * Reconciliation is tied to the account that started it. A switched or signed-out account
     * cancels before any API result can update the newly active user's durable state.
     */
    suspend fun reconcile(expectedUserId: String? = null): SyncReconciliation = reconciliationMutex.withLock {
        val session = sessionStore.current() ?: throw accountChanged()
        val userId = session.user.id
        if (expectedUserId != null && expectedUserId != userId) throw accountChanged()
        val role = session.user.role
        requireActiveUser(userId)
        var cursor = state.syncCursorFor(userId) ?: throw accountChanged()
        val received = mutableListOf<SyncEvent>()

        repeat(MAX_SYNC_PAGES) {
            requireActiveUser(userId)
            val events: List<SyncEvent>
            val nextCursor: String
            val hasMore: Boolean
            if (role == UserRole.WORKER) {
                val workerPage = marketplaceRepository.workerSync(cursor)
                requireActiveUser(userId)
                if (!sessionStore.updateIfActiveUser(userId) {
                    state.recordWorkerSync(
                        userId = userId,
                        jobs = workerPage.jobs,
                        snapshotJobs = workerPage.snapshot_jobs,
                        removedJobIds = workerPage.removed_job_ids,
                    )
                }) throw accountChanged()
                events = workerPage.events
                nextCursor = workerPage.next_cursor
                hasMore = workerPage.has_more
            } else {
                val page = marketplaceRepository.sync(cursor)
                requireActiveUser(userId)
                events = page.events
                nextCursor = page.next_cursor
                hasMore = page.has_more
            }
            if (!sessionStore.updateIfActiveUser(userId) {
                state.recordSync(userId, events, nextCursor)
            }) throw accountChanged()
            received += events

            if (!hasMore) return@withLock SyncReconciliation(received, reachedEnd = true)
            if (nextCursor == cursor) {
                throw NetworkPeerApiException(
                    code = "SYNC_CURSOR_STALLED",
                    message = "Sync returned more events without advancing its cursor. Please retry.",
                )
            }
            cursor = nextCursor
        }

        SyncReconciliation(received, reachedEnd = false)
    }

    private fun requireActiveUser(userId: String) {
        if (!sessionStore.isActiveUser(userId) || !state.isActiveUser(userId)) throw accountChanged()
    }

    private fun accountChanged(): CancellationException =
        CancellationException("The authenticated account changed during synchronization.")

    private companion object {
        const val MAX_SYNC_PAGES = 100
    }
}
