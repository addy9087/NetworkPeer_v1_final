package com.networkpeer.mobile.ui

import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.networkpeer.mobile.AppContainer
import com.networkpeer.mobile.R
import com.networkpeer.mobile.core.model.JobStatus
import com.networkpeer.mobile.core.model.NetworkPeerApiException
import com.networkpeer.mobile.core.model.UserRole
import com.networkpeer.mobile.ui.theme.BrandTeal
import com.networkpeer.mobile.ui.theme.Danger
import com.networkpeer.mobile.ui.theme.Success
import com.networkpeer.mobile.ui.theme.Warning
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

@Composable
fun NetworkPeerApp(container: AppContainer) {
    val session by container.client.sessionStore.session.collectAsState()
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            !container.client.configuration.apiConfigured -> MissingConfigurationScreen()
            session == null -> AuthScreen(container)
            else -> {
                val activeSession = requireNotNull(session)
                key(activeSession.user.id, activeSession.user.role) {
                    ReleaseAuthenticatedApp(container, activeSession)
                }
            }
        }
    }
}

@Composable
private fun MissingConfigurationScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        BrandMark()
        Spacer(Modifier.height(28.dp))
        Text(stringResource(R.string.configuration_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.configuration_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))
        InlineNotice(stringResource(R.string.configuration_api_label), BrandTeal)
    }
}

@Composable
private fun AuthScreen(container: AppContainer) {
    val context = LocalContext.current
    var phone by rememberSaveable { mutableStateOf("") }
    var otp by rememberSaveable { mutableStateOf("") }
    var roleName by rememberSaveable { mutableStateOf(UserRole.CLIENT.name) }
    var otpRequested by rememberSaveable { mutableStateOf(false) }
    var challengeId by rememberSaveable { mutableStateOf("") }
    var deliveryNote by rememberSaveable { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val role = UserRole.valueOf(roleName)

    fun resetOtpRequest() {
        otpRequested = false
        otp = ""
        challengeId = ""
        deliveryNote = null
        error = null
    }

    suspend fun requestCode() {
        val result = container.authRepository.requestOtp(phone.trim(), role)
        otpRequested = true
        challengeId = result.challengeId
        otp = ""
        deliveryNote = if (result.delivery.transport?.equals("sms", ignoreCase = true) == true) {
            context.getString(R.string.otp_sent)
        } else {
            context.getString(R.string.otp_requested)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            BrandMark()
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.auth_headline), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.auth_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(stringResource(R.string.auth_card_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.auth_card_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { value ->
                            if (phone != value && otpRequested) resetOtpRequest()
                            phone = value
                            error = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.phone_number)) },
                        placeholder = { Text(stringResource(R.string.phone_example)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        isError = error != null,
                    )
                    Text(stringResource(R.string.role_prompt), style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = role == UserRole.CLIENT,
                            onClick = { roleName = UserRole.CLIENT.name },
                            label = { Text(stringResource(R.string.client)) },
                        )
                        FilterChip(
                            selected = role == UserRole.WORKER,
                            onClick = { roleName = UserRole.WORKER.name },
                            label = { Text(stringResource(R.string.worker)) },
                        )
                    }
                    if (otpRequested) {
                        OutlinedTextField(
                            value = otp,
                            onValueChange = { otp = it.filter(Char::isDigit).take(8); error = null },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.verification_code)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = ::resetOtpRequest, enabled = !loading) {
                                Text(stringResource(R.string.edit_phone_number))
                            }
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        loading = true
                                        error = null
                                        try {
                                            requestCode()
                                        } catch (failure: Throwable) {
                                            error = friendlyError(context, failure)
                                        } finally {
                                            loading = false
                                        }
                                    }
                                },
                                enabled = phone.isNotBlank() && !loading,
                            ) {
                                Text(stringResource(R.string.resend_verification))
                            }
                        }
                    }
                    deliveryNote?.let { InlineNotice(it, BrandTeal) }
                    error?.let { InlineNotice(it, Danger) }
                    Button(
                        onClick = {
                            scope.launch {
                                loading = true
                                error = null
                                try {
                                    if (otpRequested) {
                                        container.authRepository.verifyOtp(phone.trim(), otp.trim(), challengeId)
                                    } else {
                                        requestCode()
                                    }
                                } catch (failure: Throwable) {
                                    error = friendlyError(context, failure)
                                } finally {
                                    loading = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = phone.isNotBlank() && (!otpRequested || otp.isNotBlank()) && !loading,
                    ) {
                        if (loading) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(stringResource(if (otpRequested) R.string.verify_continue else R.string.send_verification))
                    }
                }
            }
        }
        item {
            Text(stringResource(R.string.auth_security_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun BrandMark(compact: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(if (compact) 30.dp else 38.dp)
                .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Work, contentDescription = null, tint = Color.White, modifier = Modifier.size(if (compact) 18.dp else 22.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.networkpeer), style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun BackHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.IconButton(onClick = onBack) {
            Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun LoadingCard(message: String) {
    Card(shape = MaterialTheme.shapes.large) {
        Row(
            Modifier.fillMaxWidth().padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text(message)
        }
    }
}

@Composable
internal fun EmptyCard(title: String, body: String) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.Work, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun InlineNotice(message: String, color: Color) {
    Surface(color = color.copy(alpha = 0.10f), shape = MaterialTheme.shapes.medium) {
        Text(message, Modifier.padding(12.dp), color = color, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun StatusPill(status: JobStatus) {
    val tone = when (status) {
        JobStatus.COMPLETED, JobStatus.APPROVED -> Success
        JobStatus.CANCELLED, JobStatus.DISPUTED -> Danger
        JobStatus.IN_PROGRESS, JobStatus.AT_LOCATION -> Warning
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(color = tone.copy(alpha = 0.12f), shape = MaterialTheme.shapes.small) {
        Text(
            text = statusLabel(status),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = tone,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
internal fun statusLabel(status: JobStatus): String = stringResource(
    when (status) {
        JobStatus.FUNDING -> R.string.status_funding
        JobStatus.POSTED -> R.string.status_posted
        JobStatus.ASSIGNED -> R.string.status_assigned
        JobStatus.EN_ROUTE -> R.string.status_en_route
        JobStatus.AT_LOCATION -> R.string.status_at_location
        JobStatus.IN_PROGRESS -> R.string.status_in_progress
        JobStatus.SUBMITTED -> R.string.status_submitted
        JobStatus.APPROVED -> R.string.status_approved
        JobStatus.COMPLETED -> R.string.status_completed
        JobStatus.CANCELLED -> R.string.status_cancelled
        JobStatus.DISPUTED -> R.string.status_disputed
    },
)

internal fun friendlyError(context: Context, failure: Throwable): String = when (failure) {
    is NetworkPeerApiException -> "${failure.code}: ${failure.message}"
    else -> context.getString(R.string.generic_request_error)
}

internal fun formatMoney(cents: Long, currency: String): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale.getDefault())
    return runCatching {
        formatter.currency = Currency.getInstance(currency)
        formatter.format(cents / 100.0)
    }.getOrElse { "$currency ${"%.2f".format(Locale.US, cents / 100.0)}" }
}
