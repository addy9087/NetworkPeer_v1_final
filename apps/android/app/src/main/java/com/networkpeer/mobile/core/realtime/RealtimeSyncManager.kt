package com.networkpeer.mobile.core.realtime

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.networkpeer.mobile.BuildConfig
import com.networkpeer.mobile.core.data.SyncRepository
import com.networkpeer.mobile.core.model.StoredSession
import com.networkpeer.mobile.core.network.NetworkPeerPublicConfiguration
import com.networkpeer.mobile.core.network.SecureSessionStore
import io.socket.client.IO
import io.socket.client.Manager
import io.socket.client.Socket
import io.socket.engineio.client.transports.WebSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.net.URI
import java.util.concurrent.CancellationException

/**
 * Socket.IO is treated only as a delivery hint. Every connect, reconnect, and event replays the
 * authenticated cursor sync API so the server remains the source of truth.
 */
class RealtimeSyncManager(
    private val configuration: NetworkPeerPublicConfiguration,
    private val sessionStore: SecureSessionStore,
    private val syncRepository: SyncRepository,
    private val scope: CoroutineScope,
) : DefaultLifecycleObserver {
    private val lock = Any()
    private var isForeground = false
    private var socket: Socket? = null
    private var activeSession: StoredSession? = null
    private var syncJob: Job? = null

    init {
        scope.launch {
            sessionStore.session.collectLatest { session ->
                synchronized(lock) {
                    activeSession = session
                    disconnectLocked()
                    if (isForeground && session != null) connectLocked()
                }
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        synchronized(lock) {
            isForeground = true
            if (activeSession != null) connectLocked()
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        synchronized(lock) {
            isForeground = false
            disconnectLocked()
        }
    }

    private fun connectLocked() {
        if (!configuration.apiConfigured || socket != null) return
        val session = activeSession ?: return
        val userId = session.user.id
        val origin = socketOrigin() ?: return
        val options = IO.Options().apply {
            forceNew = true
            path = SOCKET_PATH
            auth = mapOf("token" to session.accessToken)
            transports = arrayOf(WebSocket.NAME)
            if (configuration.realtimeOrigin.isNotBlank()) {
                extraHeaders = mapOf("Origin" to listOf(configuration.realtimeOrigin))
            }
            reconnection = true
            timeout = SOCKET_TIMEOUT_MILLIS
        }
        val next = runCatching { IO.socket(URI(origin), options) }
            .onFailure { Log.w(TAG, "Socket.IO configuration is invalid", it) }
            .getOrNull() ?: return

        next.on(Socket.EVENT_CONNECT) { requestReconciliation(userId) }
        next.on(Manager.EVENT_RECONNECT) { requestReconciliation(userId) }
        next.on("sync:ready") { requestReconciliation(userId) }
        next.on("sync:event") { requestReconciliation(userId) }
        next.on(Socket.EVENT_CONNECT_ERROR) { Log.w(TAG, "Socket.IO connection failed") }
        socket = next
        next.connect()
    }

    private fun disconnectLocked() {
        syncJob?.cancel()
        syncJob = null
        socket?.off()
        socket?.disconnect()
        socket = null
    }

    private fun requestReconciliation(expectedUserId: String) {
        synchronized(lock) {
            if (syncJob?.isActive == true) return
            val userId = activeSession?.user?.id?.takeIf { it == expectedUserId } ?: return
            syncJob = scope.launch {
                try {
                    syncRepository.reconcile(userId)
                } catch (failure: CancellationException) {
                    throw failure
                } catch (failure: Throwable) {
                    Log.w(TAG, "Realtime reconciliation failed", failure)
                }
            }
        }
    }

    private fun socketOrigin(): String? {
        val configured = configuration.realtimeUrl.takeIf { it.isNotBlank() }
        if (configured != null && !configuration.realtimeConfigured) {
            Log.w(TAG, "Realtime URL is not permitted for this build")
            return null
        }
        val source = configured ?: configuration.apiBaseUrl
        return runCatching {
            val uri = URI(source)
            val scheme = uri.scheme?.lowercase() ?: throw IllegalArgumentException("Missing realtime URL scheme")
            val secure = scheme == "https" || scheme == "wss"
            val developmentInsecureTransport = BuildConfig.DEBUG && !configuration.realtimeSecureTransportRequired
            require(scheme in setOf("https", "wss", "http", "ws"))
            require(secure || developmentInsecureTransport)
            require(uri.userInfo == null && !uri.authority.isNullOrBlank())
            val socketScheme = when (scheme) {
                "wss" -> "https"
                "ws" -> "http"
                else -> scheme
            }
            "$socketScheme://${uri.authority}"
        }.onFailure {
            Log.w(TAG, "Realtime URL is invalid or uses an insecure production transport", it)
        }.getOrNull()
    }

    private companion object {
        const val TAG = "NetworkPeerRealtime"
        const val SOCKET_PATH = "/api/v1/realtime"
        const val SOCKET_TIMEOUT_MILLIS = 20_000L
    }
}
