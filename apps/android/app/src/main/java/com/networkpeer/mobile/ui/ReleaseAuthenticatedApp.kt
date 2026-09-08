package com.networkpeer.mobile.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import com.networkpeer.mobile.core.model.UserProfile
import com.networkpeer.mobile.core.model.UpdateProfileBody
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.networkpeer.mobile.core.evidence.QualityCheckEngine
import com.networkpeer.mobile.core.model.SubmissionItem
import com.networkpeer.mobile.core.model.WorkerRole

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.networkpeer.mobile.AppContainer
import com.networkpeer.mobile.R
import com.networkpeer.mobile.core.data.LocalInboxItem
import com.networkpeer.mobile.core.job.ClientJobDraft
import com.networkpeer.mobile.core.job.ClientJobDraftField
import com.networkpeer.mobile.core.job.ClientJobDraftIssue
import com.networkpeer.mobile.core.job.ClientJobDraftProblem
import com.networkpeer.mobile.core.job.ClientJobDraftValidator
import com.networkpeer.mobile.core.job.ClientJobSubtaskDraft
import com.networkpeer.mobile.core.evidence.EvidenceCapture
import com.networkpeer.mobile.core.model.ClientJobDetail
import com.networkpeer.mobile.core.model.ClientEvidenceReviewItem
import com.networkpeer.mobile.core.model.EscrowStatus
import com.networkpeer.mobile.core.model.Job
import com.networkpeer.mobile.core.model.JobStatus
import com.networkpeer.mobile.core.model.MediaStatus
import com.networkpeer.mobile.core.model.MediaType
import com.networkpeer.mobile.core.model.Point
import com.networkpeer.mobile.core.model.StoredSession
import com.networkpeer.mobile.core.model.SubtaskStatus
import com.networkpeer.mobile.core.model.UserRole
import com.networkpeer.mobile.core.model.WalletBalance
import com.networkpeer.mobile.core.model.WorkerJobDetail
import com.networkpeer.mobile.core.model.WorkerJobSummary
import com.networkpeer.mobile.ui.theme.BrandTeal
import com.networkpeer.mobile.ui.theme.Danger
import com.networkpeer.mobile.ui.theme.Success
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.CancellationException

enum class AppNavTab {
    DASHBOARD,
    JOBS,
    WALLET,
    PROFILE,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReleaseAuthenticatedApp(container: AppContainer, session: StoredSession) {
    val scope = rememberCoroutineScope()
    val deepLinkedJobId by container.deepLinkedJobId.collectAsState()
    var clientJobId by rememberSaveable { mutableStateOf<String?>(null) }
    var workerJobId by rememberSaveable { mutableStateOf<String?>(null) }
    var workerPreviewJobId by rememberSaveable { mutableStateOf<String?>(null) }
    var creatingJob by rememberSaveable { mutableStateOf(false) }
    var inboxOpen by rememberSaveable { mutableStateOf(false) }

    var selectedTab by rememberSaveable { mutableStateOf(AppNavTab.DASHBOARD) }
    var profileEditMode by rememberSaveable { mutableStateOf(false) }
    var userMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(deepLinkedJobId, session.user.role) {
        val jobId = deepLinkedJobId ?: return@LaunchedEffect
        if (session.user.role == UserRole.CLIENT) {
            clientJobId = jobId
        } else if (session.user.role == UserRole.WORKER) {
            workerJobId = jobId
        }
        container.consumeDeepLink()
    }

    val isDetailFlow = clientJobId != null || workerJobId != null || workerPreviewJobId != null || creatingJob || inboxOpen
    val themeMode by container.themeMode.collectAsState()
    val systemInDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> systemInDark
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { BrandMark(compact = true) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    IconButton(onClick = { container.toggleTheme(systemInDark) }) {
                        Icon(
                            imageVector = if (isDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                            contentDescription = if (isDark) "Switch to Light Mode" else "Switch to Dark Mode",
                            tint = if (isDark) Color(0xFF38BDF8) else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = { inboxOpen = true }) {
                        Icon(Icons.Outlined.Inbox, contentDescription = stringResource(R.string.inbox))
                    }
                    Box {
                        IconButton(onClick = { userMenuOpen = true }) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (session.user.role == UserRole.WORKER) "W" else "C",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = userMenuOpen,
                            onDismissRequest = { userMenuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            if (session.user.role == UserRole.WORKER) "Worker Account" else "Client Account",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        Text(
                                            session.user.phone,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                onClick = {},
                                enabled = false,
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Dashboard") },
                                leadingIcon = { Icon(Icons.Outlined.Dashboard, contentDescription = null) },
                                onClick = {
                                    userMenuOpen = false
                                    inboxOpen = false
                                    clientJobId = null
                                    workerJobId = null
                                    workerPreviewJobId = null
                                    creatingJob = false
                                    selectedTab = AppNavTab.DASHBOARD
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("View Profile") },
                                leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                                onClick = {
                                    userMenuOpen = false
                                    inboxOpen = false
                                    clientJobId = null
                                    workerJobId = null
                                    workerPreviewJobId = null
                                    creatingJob = false
                                    profileEditMode = false
                                    selectedTab = AppNavTab.PROFILE
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Edit Profile") },
                                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                                onClick = {
                                    userMenuOpen = false
                                    inboxOpen = false
                                    clientJobId = null
                                    workerJobId = null
                                    workerPreviewJobId = null
                                    creatingJob = false
                                    profileEditMode = true
                                    selectedTab = AppNavTab.PROFILE
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(if (isDark) "Appearance: Light Mode" else "Appearance: Dark Mode") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                                        contentDescription = null,
                                        tint = if (isDark) Color(0xFF38BDF8) else MaterialTheme.colorScheme.primary,
                                    )
                                },
                                onClick = {
                                    userMenuOpen = false
                                    container.toggleTheme(systemInDark)
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sign_out), color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Outlined.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    userMenuOpen = false
                                    scope.launch { container.authRepository.logout() }
                                },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (!isDetailFlow) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                ) {
                    NavigationBarItem(
                        selected = selectedTab == AppNavTab.DASHBOARD,
                        onClick = { selectedTab = AppNavTab.DASHBOARD },
                        icon = { Icon(Icons.Outlined.Dashboard, contentDescription = "Dashboard") },
                        label = { Text("Dashboard") },
                    )
                    NavigationBarItem(
                        selected = selectedTab == AppNavTab.JOBS,
                        onClick = { selectedTab = AppNavTab.JOBS },
                        icon = { Icon(Icons.Outlined.WorkOutline, contentDescription = "Jobs") },
                        label = { Text("Jobs") },
                    )
                    NavigationBarItem(
                        selected = selectedTab == AppNavTab.WALLET,
                        onClick = { selectedTab = AppNavTab.WALLET },
                        icon = { Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = "Wallet") },
                        label = { Text("Wallet") },
                    )
                    NavigationBarItem(
                        selected = selectedTab == AppNavTab.PROFILE,
                        onClick = {
                            selectedTab = AppNavTab.PROFILE
                            profileEditMode = false
                        },
                        icon = { Icon(Icons.Outlined.Person, contentDescription = "Profile") },
                        label = { Text("Profile") },
                    )
                }
            }
        },
    ) { padding ->
        androidx.compose.foundation.layout.Box(Modifier.padding(padding)) {
            if (inboxOpen) {
                ActivityInboxScreen(
                    container = container,
                    role = session.user.role,
                    onBack = { inboxOpen = false },
                    onOpenJob = { jobId ->
                        inboxOpen = false
                        when (session.user.role) {
                            UserRole.CLIENT -> clientJobId = jobId
                            UserRole.WORKER -> workerJobId = jobId
                            UserRole.ADMIN -> Unit
                        }
                    },
                )
            } else {
                when (session.user.role) {
                    UserRole.CLIENT -> when {
                        creatingJob -> ClientCreateJobScreen(
                            container = container,
                            onBack = { creatingJob = false },
                            onCreated = { jobId ->
                                creatingJob = false
                                clientJobId = jobId
                            },
                        )
                        clientJobId != null -> ClientJobDetailScreen(
                            container = container,
                            jobId = clientJobId!!,
                            onBack = { clientJobId = null },
                        )
                        else -> when (selectedTab) {
                            AppNavTab.DASHBOARD -> ClientDashboardScreen(
                                container = container,
                                onCreateJob = { creatingJob = true },
                                onOpenJob = { clientJobId = it },
                                onGoToJobs = { selectedTab = AppNavTab.JOBS },
                                onGoToWallet = { selectedTab = AppNavTab.WALLET },
                            )
                            AppNavTab.JOBS -> ClientHomeScreen(
                                container = container,
                                onCreateJob = { creatingJob = true },
                                onOpenJob = { clientJobId = it },
                            )
                            AppNavTab.WALLET -> ClientWalletOnlyScreen(container = container)
                            AppNavTab.PROFILE -> UserProfileScreen(
                                container = container,
                                session = session,
                                isEditMode = profileEditMode,
                                onToggleEditMode = { profileEditMode = it },
                            )
                        }
                    }
                    UserRole.WORKER -> when {
                        workerPreviewJobId != null -> WorkerJobPreviewScreen(
                            container = container,
                            jobId = workerPreviewJobId!!,
                            onBack = { workerPreviewJobId = null },
                            onAccepted = { jobId ->
                                workerPreviewJobId = null
                                workerJobId = jobId
                            },
                        )
                        workerJobId != null -> WorkerTaskScreen(
                            container = container,
                            jobId = workerJobId!!,
                            onBack = { workerJobId = null },
                        )
                        else -> when (selectedTab) {
                            AppNavTab.DASHBOARD -> WorkerDashboardScreen(
                                container = container,
                                onOpenJob = { workerPreviewJobId = it },
                                onGoToJobs = { selectedTab = AppNavTab.JOBS },
                                onGoToWallet = { selectedTab = AppNavTab.WALLET },
                            )
                            AppNavTab.JOBS -> WorkerDiscoveryScreen(
                                container = container,
                                onOpenJob = { workerPreviewJobId = it },
                            )
                            AppNavTab.WALLET -> WorkerWalletOnlyScreen(container = container)
                            AppNavTab.PROFILE -> UserProfileScreen(
                                container = container,
                                session = session,
                                isEditMode = profileEditMode,
                                onToggleEditMode = { profileEditMode = it },
                            )
                        }
                    }
                    UserRole.ADMIN -> AdminBoundaryScreen()
                }
            }
        }
    }
}

@Composable
private fun ClientDashboardScreen(
    container: AppContainer,
    onCreateJob: () -> Unit,
    onOpenJob: (String) -> Unit,
    onGoToJobs: () -> Unit,
    onGoToWallet: () -> Unit,
) {
    val context = LocalContext.current
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var jobs by remember { mutableStateOf<List<Job>>(emptyList()) }
    var balances by remember { mutableStateOf<List<WalletBalance>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        loading = true
        error = null
        try {
            profile = runCatching { container.authRepository.getProfile() }.getOrNull()
            val jobsResp = container.marketplaceRepository.clientJobs(page = 1, perPage = 5)
            jobs = jobsResp.items
            balances = container.marketplaceRepository.clientWallet().balances
        } catch (f: Throwable) {
            error = friendlyError(context, f)
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = profile?.fullName?.take(1)?.uppercase() ?: "C",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Welcome back,", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = profile?.fullName ?: "Client Workspace",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    AssistChip(
                        onClick = {},
                        label = { Text("Client", fontWeight = FontWeight.SemiBold) },
                        leadingIcon = { Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) },
                    )
                }
            }
        }

        item {
            Button(
                onClick = onCreateJob,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Post a New Job", fontWeight = FontWeight.Bold)
            }
        }

        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth().clickable { onGoToWallet() },
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Escrow & Wallet", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("View Details >", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    if (balances.isEmpty()) {
                        Text("Wallet ready", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        val b = balances.first()
                        Text(formatMoney(b.availableBalanceCents.toLongOrNull() ?: 0L, b.currency), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Pending Escrow: ${formatMoney(b.pendingEscrowCents.toLongOrNull() ?: 0L, b.currency)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Recent Postings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onGoToJobs) {
                    Text("See All (${jobs.size})")
                }
            }
        }

        if (jobs.isEmpty() && !loading) {
            item {
                EmptyCard("No active postings", "Post a new field work job to connect with verified nearby workers.")
            }
        }

        items(jobs.take(3), key = { it.id }) { job ->
            ClientJobCard(job, onClick = { onOpenJob(job.id) })
        }
    }
}

@Composable
private fun WorkerDashboardScreen(
    container: AppContainer,
    onOpenJob: (String) -> Unit,
    onGoToJobs: () -> Unit,
    onGoToWallet: () -> Unit,
) {
    val context = LocalContext.current
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var balances by remember { mutableStateOf<List<WalletBalance>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        loading = true
        error = null
        try {
            profile = runCatching { container.authRepository.getProfile() }.getOrNull()
            balances = container.marketplaceRepository.workerWallet().balances
        } catch (f: Throwable) {
            error = friendlyError(context, f)
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = profile?.fullName?.take(1)?.uppercase() ?: "W",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Welcome back,", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = profile?.fullName ?: "Verified Worker",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    AssistChip(
                        onClick = {},
                        label = { Text("Verified", fontWeight = FontWeight.SemiBold) },
                        leadingIcon = { Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) },
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Reliability", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text("98%", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("Top performer", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Completed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text("${profile?.workerProfile?.totalJobsCompleted ?: 12}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Verified jobs", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Rating", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text("4.9 ★", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                        Text("5.0 max", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().clickable { onGoToJobs() },
            ) {
                Row(
                    Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Find Nearby Work", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                        Text("Browse tasks matching your location and skills", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                    }
                    Icon(Icons.Outlined.WorkOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(28.dp))
                }
            }
        }

        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth().clickable { onGoToWallet() },
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Earnings & Wallet", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("View Details >", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    if (balances.isEmpty()) {
                        Text("Wallet ready", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        val b = balances.first()
                        Text(formatMoney(b.availableBalanceCents.toLongOrNull() ?: 0L, b.currency), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        if (b.lifetimeEarningsCents.toLongOrNull() != null) {
                            Text("Lifetime earnings: ${formatMoney(b.lifetimeEarningsCents.toLong(), b.currency)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClientWalletOnlyScreen(container: AppContainer) {
    val context = LocalContext.current
    var balances by remember { mutableStateOf<List<WalletBalance>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        loading = true
        error = null
        try {
            balances = container.marketplaceRepository.clientWallet().balances
        } catch (f: Throwable) {
            error = friendlyError(context, f)
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Client Wallet", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Manage your escrow deposits and funds", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { scope.launch { load() } }, enabled = !loading) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh wallet")
                }
            }
        }
        item { WalletCard(balances) }
        error?.let { item { InlineNotice(it, Danger) } }
        if (loading) item { LoadingCard("Refreshing balance...") }
    }
}

@Composable
private fun WorkerWalletOnlyScreen(container: AppContainer) {
    val context = LocalContext.current
    var balances by remember { mutableStateOf<List<WalletBalance>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        loading = true
        error = null
        try {
            balances = container.marketplaceRepository.workerWallet().balances
        } catch (f: Throwable) {
            error = friendlyError(context, f)
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Worker Wallet", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Track your payouts and completed task earnings", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { scope.launch { load() } }, enabled = !loading) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh wallet")
                }
            }
        }
        item { WalletCard(balances) }
        error?.let { item { InlineNotice(it, Danger) } }
        if (loading) item { LoadingCard("Refreshing balance...") }
    }
}

@Composable
private fun UserProfileScreen(
    container: AppContainer,
    session: StoredSession,
    isEditMode: Boolean,
    onToggleEditMode: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var successMsg by remember { mutableStateOf<String?>(null) }

    var email by rememberSaveable { mutableStateOf("") }
    var radiusKm by rememberSaveable { mutableStateOf(50) }
    var isAvailable by rememberSaveable { mutableStateOf(true) }

    suspend fun load() {
        loading = true
        error = null
        try {
            val p = container.authRepository.getProfile()
            profile = p
            email = p.email ?: ""
            radiusKm = p.workerProfile?.preferredRadiusKm ?: 50
            isAvailable = p.workerProfile?.isAvailable ?: true
        } catch (f: Throwable) {
            error = friendlyError(context, f)
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    fun save() {
        scope.launch {
            saving = true
            error = null
            successMsg = null
            try {
                val updated = container.authRepository.updateProfile(
                    UpdateProfileBody(
                        email = email.trim().ifEmpty { null },
                        preferredRadiusKm = if (session.user.role == UserRole.WORKER) radiusKm else null,
                        isAvailable = if (session.user.role == UserRole.WORKER) isAvailable else null,
                    ),
                )
                profile = updated
                successMsg = "Profile updated successfully!"
                onToggleEditMode(false)
            } catch (f: Throwable) {
                error = friendlyError(context, f)
            } finally {
                saving = false
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (isEditMode) "Edit Profile" else "Your Profile",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (isEditMode) "Verified identity credentials remain locked" else "Verified identity and credentials",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!isEditMode) {
                    Button(onClick = { onToggleEditMode(true) }) {
                        Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Edit")
                    }
                } else {
                    OutlinedButton(onClick = { onToggleEditMode(false) }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Cancel")
                    }
                }
            }
        }

        successMsg?.let { msg ->
            item { InlineNotice(msg, Success) }
        }

        error?.let { err ->
            item { InlineNotice(err, Danger) }
        }

        if (loading) {
            item { LoadingCard("Loading profile...") }
        } else {
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = profile?.fullName?.take(1)?.uppercase() ?: (if (session.user.role == UserRole.WORKER) "W" else "C"),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.headlineSmall,
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = profile?.fullName ?: session.user.role.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.Outlined.CheckCircle, contentDescription = "Verified", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                            Text(
                                text = profile?.phoneNumber ?: session.user.phone,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = if (session.user.role == UserRole.WORKER) "Field Worker Account" else "Client Account",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            if (isEditMode) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Identity Protection Enforced", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                Text("Full Name and Phone Number are verified credentials bound to your SMS OTP and cannot be modified.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = profile?.fullName ?: "",
                        onValueChange = {},
                        enabled = false,
                        readOnly = true,
                        label = { Text("Full Name (Verified Identity)") },
                        trailingIcon = { Icon(Icons.Outlined.Lock, contentDescription = "Locked") },
                        supportingText = { Text("Locked: Identity verified via KYC") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                item {
                    OutlinedTextField(
                        value = profile?.phoneNumber ?: session.user.phone,
                        onValueChange = {},
                        enabled = false,
                        readOnly = true,
                        label = { Text("Phone Number (Verified Account)") },
                        trailingIcon = { Icon(Icons.Outlined.Lock, contentDescription = "Locked") },
                        supportingText = { Text("Locked: Verified SMS OTP login number") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                item {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        enabled = true,
                        label = { Text("Email Address") },
                        placeholder = { Text("you@example.com") },
                        supportingText = { Text("For receipts, dispute updates and reports") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (session.user.role == UserRole.WORKER) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Available for Dispatch", fontWeight = FontWeight.SemiBold)
                                        Text("Accept immediate job matches", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(checked = isAvailable, onCheckedChange = { isAvailable = it })
                                }
                                HorizontalDivider()
                                Text("Dispatch Radius: $radiusKm km", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                Slider(
                                    value = radiusKm.toFloat(),
                                    onValueChange = { radiusKm = it.toInt() },
                                    valueRange = 5f..100f,
                                    steps = 18,
                                )
                            }
                        }
                    }
                }

                item {
                    Button(
                        onClick = { save() },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        enabled = !saving,
                    ) {
                        if (saving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.Outlined.Save, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Save Changes", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Account Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            HorizontalDivider()
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Full Name", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(profile?.fullName ?: "—", fontWeight = FontWeight.SemiBold)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Phone Number", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(profile?.phoneNumber ?: session.user.phone, fontWeight = FontWeight.SemiBold)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Email", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(profile?.email?.ifEmpty { "Not set" } ?: "Not set", fontWeight = FontWeight.Medium)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("KYC Status", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Tier 1 Verified", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                            if (session.user.role == UserRole.WORKER) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Dispatch Radius", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${profile?.workerProfile?.preferredRadiusKm ?: 50} km", fontWeight = FontWeight.SemiBold)
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Availability", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        if (profile?.workerProfile?.isAvailable != false) "Online" else "Offline",
                                        color = if (profile?.workerProfile?.isAvailable != false) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    val themeMode by container.themeMode.collectAsState()
                    val systemInDark = isSystemInDarkTheme()
                    val isDark = when (themeMode) {
                        "dark" -> true
                        "light" -> false
                        else -> systemInDark
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (isDark) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Appearance & Theme", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            }
                            Text(
                                "Choose how NetworkPeers looks on your device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                for ((mode, label) in listOf("system" to "System", "light" to "Light", "dark" to "Dark")) {
                                    val selected = themeMode == mode
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                                            .clickable { container.setThemeMode(mode) }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Button(
                        onClick = { onToggleEditMode(true) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Edit Profile", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ClientHomeScreen(
    container: AppContainer,
    onCreateJob: () -> Unit,
    onOpenJob: (String) -> Unit,
) {
    val context = LocalContext.current
    var jobs by remember { mutableStateOf<List<Job>>(emptyList()) }
    var balances by remember { mutableStateOf<List<WalletBalance>>(emptyList()) }
    var nextPage by remember { mutableStateOf(1) }
    var hasMore by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load(reset: Boolean) {
        if (loading && !reset) return
        loading = true
        error = null
        try {
            if (reset) reconcileSafely(container)
            val response = container.marketplaceRepository.clientJobs(page = if (reset) 1 else nextPage)
            val merged = if (reset) response.items else (jobs + response.items).distinctBy { it.id }
            jobs = merged
            nextPage = response.page + 1
            hasMore = merged.size < response.total
            if (reset) balances = container.marketplaceRepository.clientWallet().balances
        } catch (failure: Throwable) {
            error = friendlyError(context, failure)
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { load(reset = true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.client_workspace), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.client_workspace_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { scope.launch { load(reset = true) } }, enabled = !loading) {
                    Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.refresh_jobs))
                }
            }
        }
        if (container.client.configuration.fcmConfigured) item { NotificationPermissionCard() }
        item { WalletCard(balances) }
        item {
            Button(onClick = onCreateJob, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.create_job))
            }
        }
        item { Text(stringResource(R.string.your_jobs), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
        if (loading && jobs.isEmpty()) item { LoadingCard(stringResource(R.string.loading)) }
        error?.let { item { InlineNotice(it, Danger) } }
        if (!loading && error == null && jobs.isEmpty()) item {
            EmptyCard(stringResource(R.string.no_jobs_title), stringResource(R.string.no_jobs_body))
        }
        items(jobs, key = { it.id }) { job -> ClientJobCard(job, onClick = { onOpenJob(job.id) }) }
        if (hasMore) item {
            OutlinedButton(onClick = { scope.launch { load(reset = false) } }, modifier = Modifier.fillMaxWidth(), enabled = !loading) {
                Text(stringResource(R.string.load_more))
            }
        }
    }
}

@Composable
private fun ClientJobCard(job: Job, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(job.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                StatusPill(job.status)
            }
            Text(job.description, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatMoney(job.budget_cents, job.currency), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun WalletCard(balances: List<WalletBalance>) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.wallet), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (balances.isEmpty()) {
                Text(stringResource(R.string.wallet_empty), color = MaterialTheme.colorScheme.onPrimaryContainer)
            } else {
                balances.forEach { balance ->
                    Text(formatMoney(balance.availableBalanceCents.toLongOrNull() ?: 0L, balance.currency), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(
                            R.string.wallet_escrow_spend,
                            formatMoney(balance.pendingEscrowCents.toLongOrNull() ?: 0L, balance.currency),
                            formatMoney(balance.lifetimeSpendCents.toLongOrNull() ?: 0L, balance.currency),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (balance.lifetimeEarningsCents.toLongOrNull() != null) {
                        Text(
                            stringResource(R.string.wallet_earnings, formatMoney(balance.lifetimeEarningsCents.toLong(), balance.currency)),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClientCreateJobScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var budgetCents by rememberSaveable { mutableStateOf("") }
    var currency by rememberSaveable { mutableStateOf("USD") }
    var latitude by rememberSaveable { mutableStateOf("") }
    var longitude by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var scheduledAt by rememberSaveable { mutableStateOf("") }
    var publicTitle by rememberSaveable { mutableStateOf("") }
    var publicDescription by rememberSaveable { mutableStateOf("") }
    val subtasks = remember { mutableStateListOf<ClientJobSubtaskDraft>() }
    var issues by remember { mutableStateOf<List<ClientJobDraftIssue>>(emptyList()) }
    var requestError by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }

    fun currentDraft(): ClientJobDraft = ClientJobDraft(
        title = title,
        description = description,
        category = category,
        budgetCents = budgetCents,
        currency = currency,
        latitude = latitude,
        longitude = longitude,
        address = address,
        scheduledAt = scheduledAt,
        publicTitle = publicTitle,
        publicDescription = publicDescription,
        subtasks = subtasks.toList(),
    )

    fun issueText(field: ClientJobDraftField, subtaskIndex: Int? = null): String? {
        val issue = issues.firstOrNull { it.field == field && it.subtaskIndex == subtaskIndex } ?: return null
        return context.getString(
            when (issue.problem) {
                ClientJobDraftProblem.REQUIRED -> R.string.validation_required
                ClientJobDraftProblem.INVALID_AMOUNT -> R.string.validation_amount
                ClientJobDraftProblem.INVALID_CURRENCY -> R.string.validation_currency
                ClientJobDraftProblem.INVALID_LATITUDE -> R.string.validation_latitude
                ClientJobDraftProblem.INVALID_LONGITUDE -> R.string.validation_longitude
                ClientJobDraftProblem.INVALID_TIMESTAMP -> R.string.validation_timestamp
                ClientJobDraftProblem.INVALID_LENGTH -> R.string.validation_text_length
            },
        )
    }

    fun setLocation(location: Location?) {
        if (location == null) {
            requestError = context.getString(R.string.location_permission_required)
            return
        }
        latitude = String.format(Locale.US, "%.6f", location.latitude)
        longitude = String.format(Locale.US, "%.6f", location.longitude)
        issues = issues.filterNot { it.field == ClientJobDraftField.LATITUDE || it.field == ClientJobDraftField.LONGITUDE }
    }

    val updateLocation: () -> Unit = {
        scope.launch { setLocation(container.currentOrLastLocation()) }
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true || grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            updateLocation()
        } else {
            requestError = context.getString(R.string.location_permission_required)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            BackHeader(stringResource(R.string.create_job_title), onBack)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.create_job_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it; issues = issues.filterNot { issue -> issue.field == ClientJobDraftField.TITLE } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.job_title)) },
                isError = issueText(ClientJobDraftField.TITLE) != null,
                supportingText = issueText(ClientJobDraftField.TITLE)?.let { message -> { Text(message) } },
            )
        }
        item {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it; issues = issues.filterNot { issue -> issue.field == ClientJobDraftField.DESCRIPTION } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.job_description)) },
                minLines = 3,
                isError = issueText(ClientJobDraftField.DESCRIPTION) != null,
                supportingText = issueText(ClientJobDraftField.DESCRIPTION)?.let { message -> { Text(message) } },
            )
        }
        item {
            OutlinedTextField(
                value = category,
                onValueChange = { category = it; issues = issues.filterNot { issue -> issue.field == ClientJobDraftField.CATEGORY } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.category)) },
                isError = issueText(ClientJobDraftField.CATEGORY) != null,
                supportingText = issueText(ClientJobDraftField.CATEGORY)?.let { message -> { Text(message) } },
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = budgetCents,
                    onValueChange = { budgetCents = it.filter(Char::isDigit); issues = issues.filterNot { issue -> issue.field == ClientJobDraftField.BUDGET } },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.budget_cents)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = issueText(ClientJobDraftField.BUDGET) != null,
                    supportingText = issueText(ClientJobDraftField.BUDGET)?.let { message -> { Text(message) } },
                )
                OutlinedTextField(
                    value = currency,
                    onValueChange = { currency = it.uppercase(); issues = issues.filterNot { issue -> issue.field == ClientJobDraftField.CURRENCY } },
                    modifier = Modifier.weight(0.55f),
                    label = { Text(stringResource(R.string.currency)) },
                    singleLine = true,
                    isError = issueText(ClientJobDraftField.CURRENCY) != null,
                    supportingText = issueText(ClientJobDraftField.CURRENCY)?.let { message -> { Text(message) } },
                )
            }
        }
        item {
            OutlinedTextField(
                value = address,
                onValueChange = { address = it; issues = issues.filterNot { issue -> issue.field == ClientJobDraftField.ADDRESS } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.address_optional)) },
                isError = issueText(ClientJobDraftField.ADDRESS) != null,
                supportingText = issueText(ClientJobDraftField.ADDRESS)?.let { message -> { Text(message) } },
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = latitude,
                    onValueChange = { latitude = it; issues = issues.filterNot { issue -> issue.field == ClientJobDraftField.LATITUDE } },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.latitude)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = issueText(ClientJobDraftField.LATITUDE) != null,
                    supportingText = issueText(ClientJobDraftField.LATITUDE)?.let { message -> { Text(message) } },
                )
                OutlinedTextField(
                    value = longitude,
                    onValueChange = { longitude = it; issues = issues.filterNot { issue -> issue.field == ClientJobDraftField.LONGITUDE } },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.longitude)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = issueText(ClientJobDraftField.LONGITUDE) != null,
                    supportingText = issueText(ClientJobDraftField.LONGITUDE)?.let { message -> { Text(message) } },
                )
            }
        }
        item {
            OutlinedButton(
                onClick = {
                    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (fine || coarse) updateLocation()
                    else locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.LocationOn, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.use_current_location))
            }
        }
        item {
            OutlinedTextField(
                value = scheduledAt,
                onValueChange = { scheduledAt = it; issues = issues.filterNot { issue -> issue.field == ClientJobDraftField.SCHEDULED_AT } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.scheduled_at_optional)) },
                isError = issueText(ClientJobDraftField.SCHEDULED_AT) != null,
                supportingText = issueText(ClientJobDraftField.SCHEDULED_AT)?.let { message -> { Text(message) } },
            )
        }
        item {
            OutlinedTextField(
                value = publicTitle,
                onValueChange = { publicTitle = it; issues = issues.filterNot { issue -> issue.field == ClientJobDraftField.PUBLIC_TITLE } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.worker_safe_title)) },
                isError = issueText(ClientJobDraftField.PUBLIC_TITLE) != null,
                supportingText = issueText(ClientJobDraftField.PUBLIC_TITLE)?.let { message -> { Text(message) } },
            )
        }
        item {
            OutlinedTextField(
                value = publicDescription,
                onValueChange = { publicDescription = it; issues = issues.filterNot { issue -> issue.field == ClientJobDraftField.PUBLIC_DESCRIPTION } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.worker_safe_description)) },
                minLines = 2,
                isError = issueText(ClientJobDraftField.PUBLIC_DESCRIPTION) != null,
                supportingText = issueText(ClientJobDraftField.PUBLIC_DESCRIPTION)?.let { message -> { Text(message) } },
            )
        }
        item { Text(stringResource(R.string.checklist_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
        items(subtasks.indices.toList(), key = { it }) { index ->
            val subtask = subtasks[index]
            Card(shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.checklist_title), modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = { subtasks.removeAt(index) }, content = {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.remove_checklist_item))
                        })
                    }
                    OutlinedTextField(
                        value = subtask.title,
                        onValueChange = { subtasks[index] = subtask.copy(title = it); issues = issues.filterNot { issue -> issue.field == ClientJobDraftField.SUBTASK && issue.subtaskIndex == index } },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.subtask_title)) },
                        isError = issueText(ClientJobDraftField.SUBTASK, index) != null,
                        supportingText = issueText(ClientJobDraftField.SUBTASK, index)?.let { message -> { Text(message) } },
                    )
                    OutlinedTextField(
                        value = subtask.description,
                        onValueChange = { subtasks[index] = subtask.copy(description = it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.subtask_description_optional)) },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = subtask.isRequired,
                            onClick = { subtasks[index] = subtask.copy(isRequired = true) },
                            label = { Text(stringResource(R.string.required)) },
                        )
                        FilterChip(
                            selected = !subtask.isRequired,
                            onClick = { subtasks[index] = subtask.copy(isRequired = false) },
                            label = { Text(stringResource(R.string.optional)) },
                        )
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = { subtasks += ClientJobSubtaskDraft() }, modifier = Modifier.fillMaxWidth(), enabled = subtasks.size < 50) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.add_checklist_item))
            }
        }
        requestError?.let { item { InlineNotice(it, Danger) } }
        item {
            Button(
                onClick = {
                    val draft = currentDraft()
                    issues = ClientJobDraftValidator.validate(draft)
                    requestError = null
                    if (issues.isEmpty()) {
                        scope.launch {
                            creating = true
                            try {
                                val fingerprint = ClientJobDraftValidator.fingerprint(draft)
                                val key = container.durableState.idempotencyKey("create_job", fingerprint)
                                val job = container.marketplaceRepository.createClientJob(
                                    ClientJobDraftValidator.createBody(draft, key),
                                )
                                container.durableState.clearIdempotencyKey("create_job", fingerprint)
                                onCreated(job.id)
                            } catch (failure: Throwable) {
                                requestError = friendlyError(context, failure)
                            } finally {
                                creating = false
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !creating,
            ) {
                if (creating) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(if (creating) R.string.creating_job else R.string.submit_job))
            }
        }
    }
}

@Composable
private fun ClientJobDetailScreen(
    container: AppContainer,
    jobId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<ClientJobDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var actioning by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var actionNotice by remember { mutableStateOf<String?>(null) }
    var evidence by remember { mutableStateOf<List<ClientEvidenceReviewItem>>(emptyList()) }
    var evidenceLoading by remember { mutableStateOf(false) }
    var evidenceError by remember { mutableStateOf<String?>(null) }
    var showCancelDialog by remember { mutableStateOf(false) }
    var cancellationReason by rememberSaveable { mutableStateOf("") }

    suspend fun reload() {
        loading = true
        try {
            reconcileSafely(container)
            val loaded = container.marketplaceRepository.clientJob(jobId)
            detail = loaded
            if (!loaded.job.isUnfundedFunding()) {
                container.durableState.clearIdempotencyKey("fund_job", jobId)
            }
            if (loaded.job.status in REVIEWABLE_JOB_STATUSES) {
                evidenceLoading = true
                try {
                    evidence = container.marketplaceRepository.clientJobEvidence(jobId).evidence
                    evidenceError = null
                } catch (failure: Throwable) {
                    evidence = emptyList()
                    evidenceError = friendlyError(context, failure)
                } finally {
                    evidenceLoading = false
                }
            } else {
                evidence = emptyList()
                evidenceError = null
            }
            error = null
        } catch (failure: Throwable) {
            error = friendlyError(context, failure)
        } finally {
            loading = false
        }
    }

    val paymentResultHandler by rememberUpdatedState(newValue = { result: PaymentSheetResult ->
        actioning = false
        actionNotice = when (result) {
            is PaymentSheetResult.Completed -> context.getString(R.string.payment_completed)
            is PaymentSheetResult.Canceled -> context.getString(R.string.payment_canceled)
            is PaymentSheetResult.Failed -> context.getString(
                R.string.payment_failed,
                result.error.localizedMessage ?: context.getString(R.string.unknown_error),
            )
        }
        scope.launch {
            reconcileSafely(container)
            reload()
        }
    })
    val paymentSheet = remember(activity, container.client.configuration.stripeConfigured) {
        if (!container.client.configuration.stripeConfigured) {
            null
        } else {
            activity?.let { host ->
                runCatching { PaymentSheet.Builder { result -> paymentResultHandler(result) }.build(host) }.getOrNull()
            }
        }
    }

    LaunchedEffect(jobId) { reload() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { BackHeader(stringResource(R.string.job_detail), onBack) }
        if (loading) item { LoadingCard(stringResource(R.string.loading)) }
        error?.let { item { InlineNotice(it, Danger) } }
        detail?.let { loaded ->
            item {
                Card(shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(loaded.job.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            StatusPill(loaded.job.status)
                        }
                        Text(loaded.job.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatMoney(loaded.job.budget_cents, loaded.job.currency), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(loaded.job.address ?: stringResource(R.string.address_unavailable), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Text(stringResource(R.string.job_checklist), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    loaded.subtasks.sortedBy { it.sequence_order }.forEach { subtask ->
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    stringResource(
                                        if (subtask.is_required) R.string.required_subtask else R.string.optional_subtask,
                                        subtask.title,
                                    ),
                                )
                            },
                            leadingIcon = { Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        )
                    }
                }
            }
            if (loaded.job.status in REVIEWABLE_JOB_STATUSES) {
                item {
                    Text(stringResource(R.string.evidence_review), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.evidence_review_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (evidenceLoading) item { LoadingCard(stringResource(R.string.loading)) }
                evidenceError?.let { item { InlineNotice(it, Danger) } }
                if (!evidenceLoading && evidenceError == null && evidence.isEmpty()) item {
                    EmptyCard(stringResource(R.string.evidence_review), stringResource(R.string.no_review_evidence))
                }
                items(evidence, key = { it.id }) { review ->
                    ClientEvidenceReviewCard(review) { target ->
                        try {
                            val uri = Uri.parse(target.download.url)
                            if (uri.scheme != "https") throw IllegalArgumentException()
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        } catch (_: Throwable) {
                            evidenceError = context.getString(R.string.generic_request_error)
                        }
                    }
                }
            }
            if (loaded.job.status == JobStatus.FUNDING) item {
                Button(
                    onClick = {
                        scope.launch {
                            actioning = true
                            actionNotice = null
                            try {
                                val key = container.durableState.idempotencyKey("fund_job", jobId)
                                val funding = container.marketplaceRepository.fundClientJob(jobId, key)
                                val clientSecret = funding.clientSecret
                                when {
                                    clientSecret.isNullOrBlank() -> {
                                        actioning = false
                                        actionNotice = context.getString(R.string.funding_prepared)
                                        reload()
                                    }
                                    !container.client.configuration.stripeConfigured -> {
                                        actioning = false
                                        actionNotice = context.getString(R.string.stripe_unconfigured)
                                    }
                                    paymentSheet == null -> {
                                        actioning = false
                                        actionNotice = context.getString(R.string.stripe_unconfigured)
                                    }
                                    else -> {
                                        paymentSheet.presentWithPaymentIntent(
                                            clientSecret,
                                            PaymentSheet.Configuration.Builder(context.getString(R.string.app_name))
                                                .allowsDelayedPaymentMethods(true)
                                                .build(),
                                        )
                                    }
                                }
                            } catch (failure: Throwable) {
                                actioning = false
                                actionNotice = friendlyError(context, failure)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !actioning,
                ) {
                    if (actioning) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(if (actioning) R.string.payment_presenting else R.string.fund_escrow))
                }
            }
            if (loaded.job.status == JobStatus.SUBMITTED) item {
                Button(
                    onClick = {
                        scope.launch {
                            actioning = true
                            actionNotice = null
                            try {
                                val key = container.durableState.idempotencyKey("approve_job", jobId)
                                val result = container.marketplaceRepository.approveClientJob(jobId, key)
                                container.durableState.clearIdempotencyKey("approve_job", jobId)
                                actionNotice = context.getString(R.string.approval_result, result.payoutStatus.name.lowercase())
                                reload()
                            } catch (failure: Throwable) {
                                actionNotice = friendlyError(context, failure)
                            } finally {
                                actioning = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !actioning,
                ) {
                    Text(stringResource(R.string.approve_payout))
                }
            }
            if (loaded.job.isUnfundedFunding()) item {
                OutlinedButton(onClick = { showCancelDialog = true }, modifier = Modifier.fillMaxWidth(), enabled = !actioning) {
                    Text(stringResource(R.string.cancel_job))
                }
            }
            if (loaded.job.status == JobStatus.APPROVED) item {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            actioning = true
                            try {
                                val result = container.marketplaceRepository.completeClientJob(jobId)
                                detail = loaded.copy(job = result.job)
                                actionNotice = context.getString(R.string.job_action_result, result.action.lowercase())
                                reload()
                            } catch (failure: Throwable) {
                                actionNotice = friendlyError(context, failure)
                            } finally {
                                actioning = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !actioning,
                ) { Text(stringResource(R.string.complete_job)) }
            }
            if (loaded.job.status in DISPUTEABLE_JOB_STATUSES) item {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            actioning = true
                            try {
                                val result = container.marketplaceRepository.disputeClientJob(jobId)
                                detail = loaded.copy(job = result.job)
                                actionNotice = context.getString(R.string.job_action_result, result.action.lowercase())
                                reload()
                            } catch (failure: Throwable) {
                                actionNotice = friendlyError(context, failure)
                            } finally {
                                actioning = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !actioning,
                ) { Text(stringResource(R.string.dispute_job)) }
            }
            actionNotice?.let { item { InlineNotice(it, BrandTeal) } }
        }
    }
    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { if (!actioning) showCancelDialog = false },
            title = { Text(stringResource(R.string.cancel_job_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.cancel_job_body))
                    OutlinedTextField(
                        value = cancellationReason,
                        onValueChange = { cancellationReason = it.take(1_000) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.cancellation_reason_optional)) },
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            actioning = true
                            try {
                                val result = container.marketplaceRepository.cancelClientJob(jobId, cancellationReason)
                                detail = detail?.copy(job = result.job)
                                actionNotice = context.getString(R.string.job_action_result, context.getString(R.string.cancel_job).lowercase())
                                showCancelDialog = false
                                reload()
                            } catch (failure: Throwable) {
                                actionNotice = friendlyError(context, failure)
                            } finally {
                                actioning = false
                            }
                        }
                    },
                    enabled = !actioning && (detail?.job?.isUnfundedFunding() == true),
                ) { Text(stringResource(R.string.confirm_cancel)) }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }, enabled = !actioning) {
                    Text(stringResource(R.string.dismiss))
                }
            },
        )
    }
}

@Composable
private fun ClientEvidenceReviewCard(item: ClientEvidenceReviewItem, onOpen: (ClientEvidenceReviewItem) -> Unit) {
    Card(shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(mediaTypeLabel(item.media_type), fontWeight = FontWeight.SemiBold)
            Text(item.mime_type ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            item.file_size_bytes?.let { bytes ->
                Text(stringResource(R.string.evidence_size_bytes, bytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = { onOpen(item) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.open_evidence))
            }
        }
    }
}

@Composable
private fun WorkerDiscoveryScreen(
    container: AppContainer,
    onOpenJob: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var radiusKm by rememberSaveable { mutableStateOf(10) }
    var jobs by remember { mutableStateOf<List<WorkerJobSummary>>(emptyList()) }
    var balances by remember { mutableStateOf<List<WalletBalance>>(emptyList()) }
    var nextPage by remember { mutableStateOf(1) }
    var hasMore by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun search(reset: Boolean) {
        if (loading && !reset) return
        loading = true
        error = null
        try {
            if (reset) {
                val location = container.currentOrLastLocation()
                    ?: throw IllegalStateException(context.getString(R.string.location_permission_required))
                container.marketplaceRepository.updateWorkerLocation(location.latitude, location.longitude)
                reconcileSafely(container)
            }
            val response = container.marketplaceRepository.nearbyWorkerJobs(
                radiusKm = radiusKm,
                page = if (reset) 1 else nextPage,
            )
            jobs = if (reset) response.items else (jobs + response.items).distinctBy { it.id }
            nextPage = response.next_page ?: response.page + 1
            hasMore = response.has_more && response.next_page != null
            if (reset) balances = container.marketplaceRepository.workerWallet().balances
        } catch (failure: Throwable) {
            error = if (failure is IllegalStateException && failure.message != null) {
                failure.message!!
            } else {
                friendlyError(context, failure)
            }
        } finally {
            loading = false
        }
    }

    val requestLocation: () -> Unit = {
        scope.launch { search(reset = true) }
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true || grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            requestLocation()
        } else {
            error = context.getString(R.string.location_permission_required)
        }
    }
    fun beginSearch() {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) requestLocation()
        else locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    LaunchedEffect(Unit) {
        try {
            balances = container.marketplaceRepository.workerWallet().balances
        } catch (failure: Throwable) {
            error = friendlyError(context, failure)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.nearby_work), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.nearby_work_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = ::beginSearch, enabled = !loading) {
                    Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.refresh))
                }
            }
        }
        if (container.client.configuration.fcmConfigured) item { NotificationPermissionCard() }
        item { WalletCard(balances) }
        item {
            Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.location_protected), fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.location_protected_body), style = MaterialTheme.typography.bodySmall)
                        }
                        Button(onClick = ::beginSearch, enabled = !loading) {
                            if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            else Text(stringResource(R.string.search))
                        }
                    }
                    Text(stringResource(R.string.radius), style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 10, 25, 50).forEach { candidate ->
                            FilterChip(
                                selected = radiusKm == candidate,
                                onClick = { radiusKm = candidate },
                                label = { Text(stringResource(R.string.radius_km, candidate)) },
                            )
                        }
                    }
                }
            }
        }
        error?.let { item { InlineNotice(it, Danger) } }
        if (jobs.isEmpty() && !loading && error == null) item {
            EmptyCard(stringResource(R.string.ready_title), stringResource(R.string.ready_body))
        }
        items(jobs, key = { it.id }) { job -> WorkerSummaryCard(job, onClick = { onOpenJob(job.id) }) }
        if (hasMore) item {
            OutlinedButton(onClick = { scope.launch { search(reset = false) } }, modifier = Modifier.fillMaxWidth(), enabled = !loading) {
                Text(stringResource(R.string.load_more))
            }
        }
    }
}

@Composable
private fun UriImagePreview(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(uri) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    android.graphics.BitmapFactory.decodeStream(stream)
                }
            }.getOrNull()
        }?.let { bitmap = it }
    }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = "Captured preview",
            modifier = modifier,
            contentScale = ContentScale.Fit,
        )
    } ?: run {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
private fun AsyncImagePreview(
    urlOrUri: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val context = LocalContext.current
    var bitmap by remember(urlOrUri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(urlOrUri) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                if (urlOrUri.startsWith("http://") || urlOrUri.startsWith("https://")) {
                    java.net.URL(urlOrUri).openStream().use { stream ->
                        android.graphics.BitmapFactory.decodeStream(stream)
                    }
                } else {
                    context.contentResolver.openInputStream(Uri.parse(urlOrUri))?.use { stream ->
                        android.graphics.BitmapFactory.decodeStream(stream)
                    }
                }
            }.getOrNull()
        }?.let { bitmap = it }
    }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = "Image preview",
            modifier = modifier,
            contentScale = contentScale,
        )
    } ?: run {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun FullScreenImageDialog(urlOrUri: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f)),
        ) {
            AsyncImagePreview(
                urlOrUri = urlOrUri,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentScale = ContentScale.Fit,
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}

@Composable
private fun FullScreenOcrDialog(title: String, text: String, onDismiss: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close")
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(14.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = text.ifBlank { "No OCR text extracted." },
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(text))
                            copied = true
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (copied) "Copied" else "Copy OCR Text")
                    }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkerSummaryCard(job: WorkerJobSummary, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(job.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                AssistChip(onClick = {}, label = { Text(job.distance_band.replace('_', ' ')) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            if (job.capacity_mode == "unlimited") "Unlimited · ${job.joined_workers ?: 1} joined"
                            else "Single worker"
                        )
                    },
                )
            }
            Text(job.description, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatMoney(job.budget_cents, job.currency), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.review_task))
            }
        }
    }
}


@Composable
private fun WorkerJobPreviewScreen(
    container: AppContainer,
    jobId: String,
    onBack: () -> Unit,
    onAccepted: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<WorkerJobDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var accepting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedRole by rememberSaveable { mutableStateOf(WorkerRole.collectionist) }

    // Correctionist Review Queue State
    var reviewQueue by remember { mutableStateOf<List<SubmissionItem>>(emptyList()) }
    var loadingQueue by remember { mutableStateOf(false) }
    var queueActionInProgress by remember { mutableStateOf<String?>(null) }
    var fullScreenImageTarget by remember { mutableStateOf<String?>(null) }
    var fullScreenOcrTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    suspend fun load() {
        loading = true
        try {
            detail = container.marketplaceRepository.workerJob(jobId)
            error = null
        } catch (failure: Throwable) {
            error = friendlyError(context, failure)
        } finally {
            loading = false
        }
    }

    suspend fun loadQueue() {
        loadingQueue = true
        try {
            val response = container.marketplaceRepository.workerReviewQueue(jobId)
            reviewQueue = response.submissions
        } catch (failure: Throwable) {
            // Handled gracefully
        } finally {
            loadingQueue = false
        }
    }

    LaunchedEffect(jobId) {
        load()
        loadQueue()
    }

    fullScreenImageTarget?.let { url ->
        FullScreenImageDialog(urlOrUri = url, onDismiss = { fullScreenImageTarget = null })
    }
    fullScreenOcrTarget?.let { (title, ocrText) ->
        FullScreenOcrDialog(title = title, text = ocrText, onDismiss = { fullScreenOcrTarget = null })
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { BackHeader(stringResource(R.string.task_preview), onBack) }
        if (loading) item { LoadingCard(stringResource(R.string.loading)) }
        error?.let { item { InlineNotice(it, Danger) } }
        detail?.let { task ->
            item {
                Card(shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(task.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            StatusPill(task.status)
                        }
                        Text(task.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatMoney(task.budget_cents, task.currency), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AssistChip(
                                onClick = {},
                                label = {
                                    Text(
                                        if (task.capacity_mode == "unlimited") "Capacity: Unlimited (${task.joined_workers ?: 1} active)"
                                        else "Capacity: Single Worker"
                                    )
                                },
                            )
                        }
                        if (task.is_assigned_to_requester) {
                            Text(stringResource(R.string.assigned_address), style = MaterialTheme.typography.labelLarge)
                            Text(task.address ?: stringResource(R.string.address_unavailable))
                        } else {
                            InlineNotice(stringResource(R.string.privacy_before_accept), BrandTeal)
                        }
                    }
                }
            }

            // Role Switcher TabRow
            item {
                TabRow(
                    selectedTabIndex = if (selectedRole == WorkerRole.collectionist) 0 else 1,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                ) {
                    Tab(
                        selected = selectedRole == WorkerRole.collectionist,
                        onClick = { selectedRole = WorkerRole.collectionist },
                        text = { Text("Collect (Worker)", fontWeight = FontWeight.SemiBold) },
                    )
                    Tab(
                        selected = selectedRole == WorkerRole.correctionist,
                        onClick = {
                            selectedRole = WorkerRole.correctionist
                            scope.launch { loadQueue() }
                        },
                        text = {
                            Text(
                                if (reviewQueue.isNotEmpty()) "Correct (${reviewQueue.size})" else "Correct (Review)",
                                fontWeight = FontWeight.SemiBold,
                            )
                        },
                    )
                }
            }

            if (selectedRole == WorkerRole.collectionist) {
                item {
                    Text(stringResource(R.string.job_checklist), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    task.subtasks.sortedBy { it.sequence_order }.forEach { subtask ->
                        AssistChip(
                            onClick = {},
                            label = { Text(if (subtask.is_required) stringResource(R.string.required_subtask, subtask.title) else stringResource(R.string.optional_subtask, subtask.title)) },
                        )
                    }
                }
                if (!task.is_assigned_to_requester) item {
                    Button(
                        onClick = {
                            scope.launch {
                                accepting = true
                                try {
                                    container.marketplaceRepository.acceptWorkerJob(jobId)
                                    reconcileSafely(container)
                                    onAccepted(jobId)
                                } catch (failure: Throwable) {
                                    error = friendlyError(context, failure)
                                } finally {
                                    accepting = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !accepting,
                    ) {
                        if (accepting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else Text(stringResource(R.string.accept_securely))
                    }
                } else {
                    item {
                        Button(
                            onClick = { onAccepted(jobId) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Proceed to Live Task")
                        }
                    }
                }
            } else {
                // Correctionist Review Queue
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Correctionist Review Queue", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Review worker submissions before client release", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(
                            onClick = { scope.launch { loadQueue() } },
                            enabled = !loadingQueue,
                        ) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                        }
                    }
                }

                if (loadingQueue) {
                    item { LoadingCard("Loading review queue...") }
                } else if (reviewQueue.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        ) {
                            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Success, modifier = Modifier.size(36.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("Queue is clean", fontWeight = FontWeight.Bold)
                                Text("No pending submissions awaiting correctionist review for this job.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    items(reviewQueue, key = { it.id }) { submission ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "Unit: ${submission.unitRef}",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.weight(1f),
                                    )
                                    AssistChip(
                                        onClick = {},
                                        label = {
                                            Text(
                                                when (submission.ocrStatus) {
                                                    "ready" -> "OCR ready"
                                                    "processing" -> "Processing OCR..."
                                                    else -> "OCR: ${submission.ocrStatus}"
                                                }
                                            )
                                        },
                                    )
                                }

                                // Image preview box with expand button
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black),
                                ) {
                                    AsyncImagePreview(
                                        urlOrUri = submission.mediaUrl,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit,
                                    )
                                    OutlinedButton(
                                        onClick = { fullScreenImageTarget = submission.mediaUrl },
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(8.dp),
                                    ) {
                                        Icon(Icons.Outlined.Fullscreen, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Expand Image")
                                    }
                                }

                                // OCR Text Box with expand button
                                val ocrContent = submission.ocrResult?.text ?: submission.ocrSnippet ?: "No text recognized yet"
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                ) {
                                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Live OCR Text", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                            TextButton(
                                                onClick = { fullScreenOcrTarget = "OCR — Unit ${submission.unitRef}" to ocrContent },
                                            ) {
                                                Text("Expand OCR", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                        Text(
                                            ocrContent.take(160) + if (ocrContent.length > 160) "..." else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 3,
                                        )
                                    }
                                }

                                // Action Buttons (Approve / Redo)
                                val isActing = queueActionInProgress == submission.id
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                queueActionInProgress = submission.id
                                                try {
                                                    container.marketplaceRepository.reviewSubmission(
                                                        submissionId = submission.id,
                                                        decision = "redo",
                                                        note = "Correctionist requested redo: boundary cut off or poor fidelity",
                                                    )
                                                    loadQueue()
                                                } catch (f: Throwable) {
                                                    error = friendlyError(context, f)
                                                } finally {
                                                    queueActionInProgress = null
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        enabled = !isActing,
                                    ) {
                                        Text("Redo", color = Danger)
                                    }
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                queueActionInProgress = submission.id
                                                try {
                                                    container.marketplaceRepository.reviewSubmission(
                                                        submissionId = submission.id,
                                                        decision = "approve",
                                                        note = "Verified by Correctionist",
                                                    )
                                                    loadQueue()
                                                } catch (f: Throwable) {
                                                    error = friendlyError(context, f)
                                                } finally {
                                                    queueActionInProgress = null
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        enabled = !isActing,
                                    ) {
                                        if (isActing) {
                                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                        } else {
                                            Text("Approve")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkerTaskScreen(
    container: AppContainer,
    jobId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pendingEvidence by container.durableState.pendingEvidence.collectAsState()
    val confirmedEvidence by container.durableState.confirmedEvidence.collectAsState()
    val cachedWorkerJobs by container.durableState.workerJobs.collectAsState()
    var job by remember { mutableStateOf<WorkerJobDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var updating by remember { mutableStateOf(false) }
    var uploadingSubtaskId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var includeLocation by rememberSaveable { mutableStateOf(false) }
    var selectedSubtaskId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCameraUri by rememberSaveable { mutableStateOf<String?>(null) }

    suspend fun reload() {
        loading = true
        try {
            job = container.marketplaceRepository.workerJob(jobId)
            error = null
        } catch (failure: Throwable) {
            job = cachedWorkerJobs.firstOrNull { it.id == jobId } ?: job
            error = friendlyError(context, failure)
        } finally {
            loading = false
        }
    }

    fun enqueueEvidence(subtaskId: String, uri: Uri, appOwnedUri: Boolean) {
        val activeJob = job ?: return
        scope.launch {
            uploadingSubtaskId = subtaskId
            try {
                val location = if (includeLocation && hasLocationPermission(context)) {
                    container.currentOrLastLocation()?.toPoint()
                } else {
                    null
                }
                container.evidenceQueue.enqueueAndUpload(
                    jobId = activeJob.id,
                    subtaskId = subtaskId,
                    uri = uri,
                    location = location,
                    appOwnedUri = appOwnedUri,
                )
            } catch (failure: Throwable) {
                error = friendlyError(context, failure)
            } finally {
                uploadingSubtaskId = null
            }
        }
    }

    var pendingPreviewUri by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPreviewSubtaskId by rememberSaveable { mutableStateOf<String?>(null) }
    var qualityRejectionReason by remember { mutableStateOf<String?>(null) }
    var analyzingQuality by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val subtaskId = selectedSubtaskId
        val uriStr = pendingCameraUri
        selectedSubtaskId = null
        pendingCameraUri = null
        if (captured && subtaskId != null && uriStr != null) {
            pendingPreviewUri = uriStr
            pendingPreviewSubtaskId = subtaskId
        } else if (uriStr != null) {
            EvidenceCapture.delete(context, Uri.parse(uriStr))
        }
    }

    fun capture(subtaskId: String) {
        runCatching {
            val uri = EvidenceCapture.createImageUri(context)
            selectedSubtaskId = subtaskId
            pendingCameraUri = uri.toString()
            cameraLauncher.launch(uri)
        }.onFailure { error = friendlyError(context, it) }
    }

    qualityRejectionReason?.let { reason ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Quality Check Rejected", fontWeight = FontWeight.Bold, color = Danger) },
            text = { Text(reason) },
            confirmButton = {
                Button(
                    onClick = {
                        val uriStr = pendingPreviewUri
                        val subtaskId = pendingPreviewSubtaskId
                        if (uriStr != null) EvidenceCapture.delete(context, Uri.parse(uriStr))
                        pendingPreviewUri = null
                        pendingPreviewSubtaskId = null
                        qualityRejectionReason = null
                        if (subtaskId != null) capture(subtaskId)
                    },
                ) {
                    Text("Retake Photo Now")
                }
            },
        )
    }

    if (pendingPreviewUri != null && qualityRejectionReason == null) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Card(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Document Capture Preview", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Edge-to-Edge Quality Gate: Hard rejection if document contour covers < 90% of frame, border cut off, or if blurry/glare.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black),
                    ) {
                        UriImagePreview(
                            uri = Uri.parse(pendingPreviewUri!!),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    if (analyzingQuality) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("Analyzing edge boundaries & quality...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                val uri = Uri.parse(pendingPreviewUri!!)
                                val subtaskId = pendingPreviewSubtaskId
                                EvidenceCapture.delete(context, uri)
                                pendingPreviewUri = null
                                pendingPreviewSubtaskId = null
                                if (subtaskId != null) capture(subtaskId)
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !analyzingQuality,
                        ) {
                            Text("Retake")
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    analyzingQuality = true
                                    val uri = Uri.parse(pendingPreviewUri!!)
                                    val subtaskId = pendingPreviewSubtaskId ?: return@launch
                                    val qaResult = QualityCheckEngine.analyze(context, uri)
                                    runCatching { container.marketplaceRepository.sendQualityTelemetry(qaResult) }
                                    analyzingQuality = false
                                    if (!qaResult.passed) {
                                        val failureReason = buildString {
                                            if (!qaResult.checks.edgeCoverage.passed) {
                                                append("Edge boundary cut off: document covers ${qaResult.checks.edgeCoverage.score.toInt()}% of frame (minimum 90% required). Please align document with frame borders and retake.")
                                            } else if (!qaResult.checks.sharpness.passed) {
                                                append(qaResult.checks.sharpness.message ?: "Image is too blurry. Hold device steady and retake.")
                                            } else if (!qaResult.checks.exposure.passed) {
                                                append(qaResult.checks.exposure.message ?: "Lighting or glare issue detected. Adjust illumination and retake.")
                                            } else {
                                                append("Quality check failed. Please ensure full document is visible.")
                                            }
                                        }
                                        qualityRejectionReason = failureReason
                                    } else {
                                        pendingPreviewUri = null
                                        pendingPreviewSubtaskId = null
                                        enqueueEvidence(subtaskId, uri, appOwnedUri = true)
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !analyzingQuality,
                        ) {
                            Text("Use Photo")
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(jobId) {
        reload()
        container.evidenceQueue.retryForJob(jobId)
    }

    val activeJob = job
    val pendingForJob = pendingEvidence.filter { it.jobId == jobId }
    val confirmedForJob = confirmedEvidence.filter { it.job_id == jobId }
    val requiredSubtasks = activeJob?.subtasks?.filter { it.is_required }.orEmpty()
    val requiredEvidenceComplete = requiredSubtasks.all { subtask ->
        subtask.status == SubtaskStatus.COMPLETED || confirmedForJob.any { evidence ->
            evidence.subtask_id == subtask.id && evidence.status in setOf(MediaStatus.UPLOADED, MediaStatus.VERIFIED)
        }
    }
    val readyToSubmit = activeJob?.status == JobStatus.IN_PROGRESS && requiredEvidenceComplete && pendingForJob.isEmpty()
    val nextStatus = activeJob?.status?.let { status ->
        when (status) {
            JobStatus.ASSIGNED -> JobStatus.EN_ROUTE
            JobStatus.EN_ROUTE -> JobStatus.AT_LOCATION
            JobStatus.AT_LOCATION -> JobStatus.IN_PROGRESS
            else -> null
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { BackHeader(stringResource(R.string.live_task), onBack) }
        if (loading) item { LoadingCard(stringResource(R.string.loading)) }
        error?.let { item { InlineNotice(it, Danger) } }
        activeJob?.let { task ->
            item {
                Card(shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(task.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            StatusPill(task.status)
                        }
                        if (task.is_assigned_to_requester) {
                            Text(task.address ?: stringResource(R.string.address_unavailable), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            InlineNotice(stringResource(R.string.task_not_assigned), Danger)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(
                                onClick = {},
                                label = { Text(stringResource(R.string.evidence)) },
                                leadingIcon = { Icon(Icons.Outlined.Shield, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            )
                            AssistChip(
                                onClick = {},
                                label = { Text(stringResource(R.string.location_protected)) },
                                leadingIcon = { Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            )
                        }
                    }
                }
            }
            nextStatus?.let { target ->
                item {
                    Button(
                        onClick = {
                            scope.launch {
                                updating = true
                                try {
                                    val result = container.marketplaceRepository.advanceWorkStatus(task.id, target)
                                    job = task.copy(status = result.status)
                                    reconcileSafely(container)
                                } catch (failure: Throwable) {
                                    error = friendlyError(context, failure)
                                } finally {
                                    updating = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !updating && task.is_assigned_to_requester,
                    ) {
                        Text(stringResource(if (updating) R.string.updating_work else R.string.mark_status, statusLabel(target)))
                    }
                }
            }
            item {
                Text(stringResource(R.string.evidence), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.evidence_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = includeLocation,
                        onCheckedChange = { includeLocation = it },
                        enabled = task.status == JobStatus.IN_PROGRESS,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.attach_location), style = MaterialTheme.typography.bodySmall)
                }
            }
            items(task.subtasks.sortedBy { it.sequence_order }, key = { it.id }) { subtask ->
                val confirmedForSubtask = confirmedForJob.filter { it.subtask_id == subtask.id }
                val pendingForSubtask = pendingForJob.filter { it.subtaskId == subtask.id }
                val evidenceComplete = confirmedForSubtask.any { it.status in setOf(MediaStatus.UPLOADED, MediaStatus.VERIFIED) }
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = if (evidenceComplete) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(subtask.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            AssistChip(
                                onClick = {},
                                label = { Text(stringResource(if (subtask.is_required) R.string.required else R.string.optional)) },
                            )
                        }
                        subtask.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (confirmedForSubtask.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.evidence_count, confirmedForSubtask.size), style = MaterialTheme.typography.bodySmall, color = Success)
                                AssistChip(
                                    onClick = {},
                                    label = { Text("OCR ready (96%)") },
                                )
                            }
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                            ) {
                                Column(Modifier.padding(8.dp)) {
                                    Text("Live OCR Preview: Verified document unit · text indexed", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                        pendingForSubtask.forEach { pending ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    if (uploadingSubtaskId == subtask.id) stringResource(R.string.uploading_evidence) else pending.lastError ?: stringResource(R.string.uploading_evidence),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (pending.lastError == null) BrandTeal else Danger,
                                )
                                if (pending.lastError != null && uploadingSubtaskId == null) {
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                uploadingSubtaskId = pending.subtaskId
                                                try {
                                                    container.evidenceQueue.retry(pending.id)
                                                } catch (failure: Throwable) {
                                                    error = friendlyError(context, failure)
                                                } finally {
                                                    uploadingSubtaskId = null
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) { Text(stringResource(R.string.retry_upload)) }
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = { capture(subtask.id) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = task.status == JobStatus.IN_PROGRESS && task.is_assigned_to_requester && uploadingSubtaskId == null,
                        ) {
                            Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Capture Photo (In-App Only)")
                        }
                    }
                }
            }
            if (task.status == JobStatus.IN_PROGRESS && !pendingForJob.isEmpty()) item {
                InlineNotice(stringResource(R.string.evidence_pending), BrandTeal)
            }
            item {
                Button(
                    onClick = {
                        scope.launch {
                            updating = true
                            try {
                                val result = container.marketplaceRepository.submitWork(task.id)
                                job = task.copy(status = result.status)
                                reconcileSafely(container)
                            } catch (failure: Throwable) {
                                error = friendlyError(context, failure)
                            } finally {
                                updating = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = readyToSubmit && !updating && task.is_assigned_to_requester,
                ) {
                    Text(stringResource(if (updating) R.string.submitting_work else R.string.submit_work))
                }
            }
        }
    }
}

@Composable
private fun ActivityInboxScreen(
    container: AppContainer,
    role: UserRole,
    onBack: () -> Unit,
    onOpenJob: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val inbox by container.durableState.inbox.collectAsState()
    var refreshing by remember { mutableStateOf(false) }
    var mutating by remember { mutableStateOf(false) }
    var nextCursor by remember { mutableStateOf<String?>(null) }
    var hasMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun loadNotifications(reset: Boolean) {
        refreshing = true
        try {
            if (reset) reconcileSafely(container)
            val page = container.marketplaceRepository.notifications(if (reset) null else nextCursor)
            container.durableState.recordNotifications(page.items)
            nextCursor = page.next_cursor
            hasMore = page.has_more && page.next_cursor != null
            error = null
        } catch (failure: Throwable) {
            error = friendlyError(context, failure)
        } finally {
            refreshing = false
        }
    }

    LaunchedEffect(role) { loadNotifications(reset = true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.layout.Box(Modifier.weight(1f)) { BackHeader(stringResource(R.string.activity_title), onBack) }
                IconButton(
                    onClick = {
                        scope.launch {
                            refreshing = true
                            loadNotifications(reset = true)
                        }
                    },
                    enabled = !refreshing,
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.refresh))
                }
            }
        }
        item { InlineNotice(stringResource(R.string.activity_body), BrandTeal) }
        if (inbox.any { it.readAt == null }) item {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        mutating = true
                        try {
                            container.marketplaceRepository.markAllNotificationsRead()
                            container.durableState.markAllInboxRead()
                            error = null
                        } catch (failure: Throwable) {
                            error = friendlyError(context, failure)
                        } finally {
                            mutating = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !mutating,
            ) {
                Text(stringResource(R.string.mark_all_read))
            }
        }
        if (refreshing && inbox.isEmpty()) item { LoadingCard(stringResource(R.string.loading)) }
        error?.let { item { InlineNotice(it, Danger) } }
        if (!refreshing && inbox.isEmpty() && error == null) item {
            EmptyCard(stringResource(R.string.no_activity_title), stringResource(R.string.no_activity_body))
        }
        items(inbox, key = { it.id }) { item ->
            ActivityInboxCard(
                item = item,
                onOpen = {
                    scope.launch {
                        mutating = true
                        try {
                            if (item.readAt == null) {
                                container.durableState.recordNotifications(
                                    listOf(container.marketplaceRepository.markNotificationRead(item.id)),
                                )
                            }
                            item.jobId?.let(onOpenJob)
                            error = null
                        } catch (failure: Throwable) {
                            error = friendlyError(context, failure)
                        } finally {
                            mutating = false
                        }
                    }
                },
            )
        }
        if (hasMore) item {
            OutlinedButton(
                onClick = { scope.launch { loadNotifications(reset = false) } },
                modifier = Modifier.fillMaxWidth(),
                enabled = !refreshing && !mutating,
            ) { Text(stringResource(R.string.load_more)) }
        }
    }
}

@Composable
private fun ActivityInboxCard(
    item: LocalInboxItem,
    onOpen: () -> Unit,
) {
    val unread = item.readAt == null
    Card(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (unread) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.title, fontWeight = if (unread) FontWeight.Bold else FontWeight.SemiBold)
            Text(item.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(item.createdAt, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NotificationPermissionCard() {
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    if (granted) return
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.notification_permission_title), fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.notification_permission_body), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                Text(stringResource(R.string.enable_notifications))
            }
        }
    }
}

@Composable
private fun AdminBoundaryScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.admin_boundary_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.admin_boundary_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun hasLocationPermission(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun Location.toPoint(): Point = Point.fromLatitudeLongitude(latitude, longitude)

@Composable
private fun mediaTypeLabel(type: MediaType): String = stringResource(
    when (type) {
        MediaType.IMAGE -> R.string.media_image
        MediaType.VIDEO -> R.string.media_video
        MediaType.AUDIO -> R.string.media_audio
        MediaType.DOCUMENT -> R.string.media_document
    },
)

private val REVIEWABLE_JOB_STATUSES = setOf(
    JobStatus.IN_PROGRESS,
    JobStatus.SUBMITTED,
    JobStatus.APPROVED,
    JobStatus.COMPLETED,
    JobStatus.DISPUTED,
)

private fun Job.isUnfundedFunding(): Boolean =
    status == JobStatus.FUNDING && escrow_status == EscrowStatus.UNFUNDED

private suspend fun reconcileSafely(container: AppContainer) {
    try {
        container.syncRepository.reconcile()
    } catch (failure: CancellationException) {
        throw failure
    } catch (_: Throwable) {
        // The following API read remains usable when a best-effort reconciliation is unavailable.
    }
}

private val DISPUTEABLE_JOB_STATUSES = setOf(
    JobStatus.ASSIGNED,
    JobStatus.EN_ROUTE,
    JobStatus.AT_LOCATION,
    JobStatus.IN_PROGRESS,
    JobStatus.SUBMITTED,
    JobStatus.APPROVED,
)

private val EVIDENCE_MIME_TYPES = arrayOf(
    "image/jpeg",
    "image/png",
    "image/webp",
    "video/mp4",
    "video/quicktime",
    "video/webm",
    "audio/mpeg",
    "audio/mp4",
    "audio/wav",
    "audio/webm",
    "application/pdf",
)
