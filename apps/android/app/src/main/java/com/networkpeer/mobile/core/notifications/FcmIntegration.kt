package com.networkpeer.mobile.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.networkpeer.mobile.MainActivity
import com.networkpeer.mobile.NetworkPeerApplication
import com.networkpeer.mobile.R
import com.networkpeer.mobile.core.data.MarketplaceRepository
import com.networkpeer.mobile.core.network.NetworkPeerPublicConfiguration
import com.networkpeer.mobile.core.network.SecureSessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CancellationException

class FcmTokenRegistrar(
    private val context: Context,
    private val configuration: NetworkPeerPublicConfiguration,
    private val sessionStore: SecureSessionStore,
    private val marketplaceRepository: MarketplaceRepository,
    private val scope: CoroutineScope,
) {
    fun registerWhenPossible() {
        val userId = sessionStore.current()?.user?.id ?: return
        if (!configuration.apiConfigured || !configuration.fcmConfigured) return
        scope.launch {
            val token = currentToken() ?: return@launch
            registerForActiveUser(userId, token)
        }
    }

    fun onNewToken(token: String) {
        val userId = sessionStore.current()?.user?.id ?: return
        if (!configuration.apiConfigured || !configuration.fcmConfigured || token.isBlank()) return
        scope.launch {
            registerForActiveUser(userId, token)
        }
    }

    suspend fun deregisterForActiveUser(userId: String) {
        if (!configuration.apiConfigured || !configuration.fcmConfigured || !sessionStore.isActiveUser(userId)) return
        val token = withTimeoutOrNull(DEREGISTRATION_TIMEOUT_MS) { currentToken() } ?: return
        if (token.isBlank() || !sessionStore.isActiveUser(userId)) return
        try {
            withTimeoutOrNull(DEREGISTRATION_TIMEOUT_MS) {
                marketplaceRepository.deregisterDevice(token)
            }
        } catch (failure: Throwable) {
            if (failure is CancellationException) throw failure
            // The next sign-in can safely register the token again.
        }
    }

    private suspend fun registerForActiveUser(userId: String, token: String) {
        if (!sessionStore.isActiveUser(userId)) return
        try {
            marketplaceRepository.registerDevice(token)
        } catch (failure: Throwable) {
            if (failure is CancellationException) throw failure
            // The next refresh or app foreground will register the token again.
        }
    }

    private suspend fun currentToken(): String? {
        val firebaseInitialized = try {
            FirebaseApp.initializeApp(context)
        } catch (failure: Throwable) {
            if (failure is CancellationException) throw failure
            null
        }
        if (firebaseInitialized == null) return null
        return try {
            FirebaseMessaging.getInstance().token.await()
        } catch (failure: Throwable) {
            if (failure is CancellationException) throw failure
            null
        }
    }

    private companion object {
        const val DEREGISTRATION_TIMEOUT_MS = 5_000L
    }
}

class NetworkPeerMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        (application as? NetworkPeerApplication)?.container?.fcmTokenRegistrar?.onNewToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val container = (application as? NetworkPeerApplication)?.container ?: return
        val activeUserId = container.client.sessionStore.current()?.user?.id ?: return
        // A data-only hint is never state authority; it only wakes the authenticated client to sync.
        if (message.notification != null) return
        val data = message.data
        if (data[RECIPIENT_USER_ID] != activeUserId || !data.isCanonicalPushHint()) return
        val title = data["title"] ?: return
        val body = data["body"] ?: return
        val cursor = data["cursor"] ?: return
        val jobId = data["job_id"]
        if (!container.client.sessionStore.isActiveUser(activeUserId)) return
        container.reconcileFromPush(activeUserId)
        NetworkPeerNotifications.show(this, activeUserId, title, body, cursor, jobId) {
            container.client.sessionStore.isActiveUser(activeUserId)
        }
    }

    private companion object {
        const val RECIPIENT_USER_ID = "recipient_user_id"

        fun Map<String, String>.isCanonicalPushHint(): Boolean =
            this["cursor"]?.isNotEmpty() == true &&
                this["cursor"]?.all(Char::isDigit) == true &&
                !this["topic"].isNullOrBlank() &&
                !this["title"].isNullOrBlank() &&
                !this["body"].isNullOrBlank()
    }
}

object NetworkPeerNotifications {
    const val CHANNEL_ID = "networkpeer_job_updates"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    @Synchronized
    fun show(
        context: Context,
        accountId: String,
        title: String,
        body: String,
        cursor: String,
        jobId: String?,
        isActiveAccount: () -> Boolean,
    ) {
        if (!isActiveAccount()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val notificationId = cursor.hashCode()
        val deepLink = jobId?.let { Uri.parse("networkpeer://job/$it") }
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = deepLink
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        val notificationManager = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return
        notificationManager.notify(notificationTag(accountId), notificationId, notification)
        trackNotification(context, accountId, notificationId)
    }

    @Synchronized
    fun cancelForAccount(context: Context, accountId: String) {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val notificationIds = preferences.getStringSet(notificationIdsKey(accountId), emptySet())
            .orEmpty()
            .mapNotNull { value -> value.toIntOrNull() }
        val notificationManager = ContextCompat.getSystemService(context, NotificationManager::class.java)
        notificationIds.forEach { notificationId ->
            notificationManager?.cancel(notificationTag(accountId), notificationId)
        }
        preferences.edit().remove(notificationIdsKey(accountId)).apply()
    }

    private fun trackNotification(context: Context, accountId: String, notificationId: Int) {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val notificationIds = preferences.getStringSet(notificationIdsKey(accountId), emptySet()).orEmpty().toMutableSet()
        notificationIds += notificationId.toString()
        preferences.edit().putStringSet(notificationIdsKey(accountId), notificationIds).apply()
    }

    private fun notificationTag(accountId: String): String = "networkpeer.account.$accountId"

    private fun notificationIdsKey(accountId: String): String = "$NOTIFICATION_IDS_PREFIX$accountId"

    private const val PREFERENCES_NAME = "networkpeer.notifications"
    private const val NOTIFICATION_IDS_PREFIX = "notification_ids."
}
