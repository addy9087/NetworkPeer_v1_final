package com.networkpeer.mobile

import android.app.Application
import android.content.Context
import android.location.Location
import android.net.Uri
import androidx.lifecycle.ProcessLifecycleOwner
import com.stripe.android.PaymentConfiguration
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.networkpeer.mobile.core.data.AuthRepository
import com.networkpeer.mobile.core.data.DurableAppState
import com.networkpeer.mobile.core.data.MarketplaceRepository
import com.networkpeer.mobile.core.data.SyncRepository
import com.networkpeer.mobile.core.evidence.DurableEvidenceUploadQueue
import com.networkpeer.mobile.core.evidence.EvidenceUploader
import com.networkpeer.mobile.core.network.NetworkPeerClient
import com.networkpeer.mobile.core.notifications.FcmTokenRegistrar
import com.networkpeer.mobile.core.notifications.NetworkPeerNotifications
import com.networkpeer.mobile.core.realtime.RealtimeSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.CancellationException

class NetworkPeerApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NetworkPeerNotifications.createChannel(this)
        if (container.client.configuration.stripeConfigured) {
            runCatching { PaymentConfiguration.init(this, container.client.configuration.stripePublishableKey) }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(container.realtimeSyncManager)
    }
}

class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val client = NetworkPeerClient(applicationContext)
    val durableState = DurableAppState(applicationContext)
    val marketplaceRepository = MarketplaceRepository(client.api)
    val syncRepository = SyncRepository(marketplaceRepository, durableState, client.sessionStore)
    val evidenceUploader = EvidenceUploader(applicationContext.contentResolver, marketplaceRepository, client.uploadClient)
    val evidenceQueue = DurableEvidenceUploadQueue(applicationContext, evidenceUploader, durableState)
    val fcmTokenRegistrar = FcmTokenRegistrar(
        context = applicationContext,
        configuration = client.configuration,
        sessionStore = client.sessionStore,
        marketplaceRepository = marketplaceRepository,
        scope = applicationScope,
    )
    val authRepository = AuthRepository(
        api = client.api,
        client = client,
        onLogout = ::clearAccountData,
        deregisterDevice = fcmTokenRegistrar::deregisterForActiveUser,
    )
    val realtimeSyncManager = RealtimeSyncManager(
        configuration = client.configuration,
        sessionStore = client.sessionStore,
        syncRepository = syncRepository,
        scope = applicationScope,
    )
    private val locationClient = LocationServices.getFusedLocationProviderClient(applicationContext)
    private val _deepLinkedJobId = MutableStateFlow<String?>(null)
    val deepLinkedJobId = _deepLinkedJobId.asStateFlow()

    private val themePreferences = applicationContext.getSharedPreferences("networkpeer_theme", Context.MODE_PRIVATE)
    private val _themeMode = MutableStateFlow(themePreferences.getString("theme_mode", "system") ?: "system")
    val themeMode = _themeMode.asStateFlow()

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        themePreferences.edit().putString("theme_mode", mode).apply()
    }

    fun toggleTheme(isSystemDark: Boolean) {
        val current = _themeMode.value
        val effectiveDark = when (current) {
            "dark" -> true
            "light" -> false
            else -> isSystemDark
        }
        val next = if (effectiveDark) "light" else "dark"
        setThemeMode(next)
    }

    init {
        val initialUserId = client.sessionStore.current()?.user?.id
        durableState.activate(initialUserId)
        applicationScope.launch {
            var previousUserId = initialUserId
            client.sessionStore.session.collectLatest { session ->
                val userId = session?.user?.id
                if (previousUserId != null && previousUserId != userId) {
                    clearAccountData(previousUserId!!)
                }
                previousUserId = userId
                durableState.activate(userId)
                if (session == null) return@collectLatest
                fcmTokenRegistrar.registerWhenPossible()
                try {
                    syncRepository.reconcile(session.user.id)
                } catch (failure: CancellationException) {
                    throw failure
                } catch (_: Throwable) {
                    // Foreground UI and realtime recovery can retry a failed reconciliation.
                }
            }
        }
    }

    private fun clearAccountData(userId: String) {
        durableState.clearUserData(userId)
        NetworkPeerNotifications.cancelForAccount(applicationContext, userId)
    }

    fun handleDeepLink(uri: Uri?) {
        val jobId = uri
            ?.takeIf { it.scheme == "networkpeer" && it.host == "job" }
            ?.pathSegments
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }
        if (jobId != null) _deepLinkedJobId.value = jobId
    }

    fun consumeDeepLink() {
        _deepLinkedJobId.value = null
    }

    fun reconcileFromPush(userId: String) {
        if (!client.sessionStore.isActiveUser(userId)) return
        applicationScope.launch {
            try {
                syncRepository.reconcile(userId)
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Throwable) {
                // Foreground and realtime recovery retry an unavailable sync API.
            }
        }
    }

    suspend fun currentLocation(): Location? = currentOrLastLocation()

    suspend fun currentOrLastLocation(): Location? {
        val current = try {
            locationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
        } catch (_: Throwable) {
            null
        }
        if (current != null) return current
        return try {
            locationClient.lastLocation.await()
        } catch (_: Throwable) {
            null
        }
    }
}
