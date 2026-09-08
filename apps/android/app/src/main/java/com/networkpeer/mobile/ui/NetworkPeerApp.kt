package com.networkpeer.mobile.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.BusinessCenter
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Engineering
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.networkpeer.mobile.AppContainer
import com.networkpeer.mobile.R
import com.networkpeer.mobile.core.model.JobStatus
import com.networkpeer.mobile.core.model.NetworkPeerApiException
import com.networkpeer.mobile.core.model.UserRole
import com.networkpeer.mobile.ui.theme.BrandSkyContainer
import com.networkpeer.mobile.ui.theme.BrandSkyLight
import com.networkpeer.mobile.ui.theme.BrandSkyPrimary
import com.networkpeer.mobile.ui.theme.BrandSkySoft
import com.networkpeer.mobile.ui.theme.BrandSkyText
import com.networkpeer.mobile.ui.theme.BrandSkyVibrant
import com.networkpeer.mobile.ui.theme.BrandTeal
import com.networkpeer.mobile.ui.theme.Danger
import com.networkpeer.mobile.ui.theme.Slate200
import com.networkpeer.mobile.ui.theme.Slate400
import com.networkpeer.mobile.ui.theme.Slate500
import com.networkpeer.mobile.ui.theme.Slate900
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
        Text(
            stringResource(R.string.configuration_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.configuration_body),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))
        InlineNotice(stringResource(R.string.configuration_api_label), BrandSkyPrimary)
    }
}

@Composable
private fun RoleSelectionCard(
    modifier: Modifier = Modifier,
    title: String,
    description: String,
    selected: Boolean,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) BrandSkySoft else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) BrandSkyPrimary else Slate200,
        ),
        shadowElevation = if (selected) 2.dp else 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            color = if (selected) BrandSkyContainer else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (selected) BrandSkyPrimary else Slate500,
                        modifier = Modifier.size(20.dp),
                    )
                }
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(BrandSkyPrimary, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (selected) BrandSkyText else Slate900,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = Slate500,
            )
        }
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
    var devOtp by rememberSaveable { mutableStateOf<String?>(null) }
    var deliveryNote by rememberSaveable { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val role = UserRole.valueOf(roleName)

    fun resetOtpRequest() {
        otpRequested = false
        otp = ""
        challengeId = ""
        devOtp = null
        deliveryNote = null
        error = null
    }

    // Normalizes input to E.164 (+91XXXXXXXXXX) format expected by server
    fun normalizePhone(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("+")) {
            return "+" + trimmed.substring(1).filter(Char::isDigit)
        }
        val digits = trimmed.filter(Char::isDigit)
        return when {
            digits.length == 10 -> "+91$digits"
            digits.length == 12 && digits.startsWith("91") -> "+$digits"
            digits.length == 11 && digits.startsWith("1") -> "+$digits"
            else -> "+$digits"
        }
    }

    suspend fun requestCode() {
        val rawDigits = phone.filter(Char::isDigit)
        if (rawDigits.length < 10 && !phone.trim().startsWith("+")) {
            error = context.getString(R.string.phone_invalid_error)
            return
        }
        val normalizedPhone = normalizePhone(phone)
        val result = container.authRepository.requestOtp(normalizedPhone, role)
        otpRequested = true
        challengeId = result.challengeId
        devOtp = result.otp
        otp = result.otp ?: ""
        deliveryNote = if (!result.otp.isNullOrBlank()) {
            context.getString(R.string.otp_development_code, result.otp)
        } else if (result.delivery.transport?.equals("sms", ignoreCase = true) == true) {
            context.getString(R.string.otp_sent)
        } else {
            context.getString(R.string.otp_requested)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            BrandMark()
            Spacer(Modifier.height(20.dp))

            Surface(
                color = BrandSkyContainer,
                shape = RoundedCornerShape(20.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.VerifiedUser,
                        contentDescription = null,
                        tint = BrandSkyPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.auth_badge),
                        color = BrandSkyPrimary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.auth_headline),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.auth_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, Slate200),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = stringResource(R.string.role_prompt),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate900,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RoleSelectionCard(
                            modifier = Modifier.weight(1f),
                            title = stringResource(R.string.client),
                            description = stringResource(R.string.role_client_desc),
                            selected = role == UserRole.CLIENT,
                            icon = Icons.Outlined.BusinessCenter,
                            onClick = { roleName = UserRole.CLIENT.name },
                        )
                        RoleSelectionCard(
                            modifier = Modifier.weight(1f),
                            title = stringResource(R.string.worker),
                            description = stringResource(R.string.role_worker_desc),
                            selected = role == UserRole.WORKER,
                            icon = Icons.Outlined.Engineering,
                            onClick = { roleName = UserRole.WORKER.name },
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.phone_number),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Slate900,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, Slate200),
                                modifier = Modifier.height(56.dp),
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = stringResource(R.string.phone_prefix),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate900,
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = phone,
                                onValueChange = { value ->
                                    if (phone != value && otpRequested) resetOtpRequest()
                                    val cleaned = if (value.startsWith("+")) {
                                        "+" + value.drop(1).filter(Char::isDigit).take(12)
                                    } else {
                                        value.filter(Char::isDigit).take(10)
                                    }
                                    phone = cleaned
                                    error = null
                                },
                                modifier = Modifier.weight(1f),
                                placeholder = {
                                    Text(
                                        text = stringResource(R.string.phone_placeholder),
                                        color = Slate400,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Phone,
                                        contentDescription = null,
                                        tint = BrandSkyPrimary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(12.dp),
                                isError = error != null,
                            )
                        }

                        Text(
                            text = stringResource(R.string.phone_helper),
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate500,
                        )
                    }

                    if (otpRequested) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = stringResource(R.string.verification_code),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = Slate900,
                            )

                            OutlinedTextField(
                                value = otp,
                                onValueChange = {
                                    otp = it.filter(Char::isDigit).take(8)
                                    error = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = {
                                    Text(
                                        text = "Enter 4-8 digit OTP",
                                        color = Slate400,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Key,
                                        contentDescription = null,
                                        tint = BrandSkyPrimary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                                trailingIcon = {
                                    if (!devOtp.isNullOrBlank() && otp != devOtp) {
                                        TextButton(
                                            onClick = { otp = devOtp!! },
                                            contentPadding = PaddingValues(horizontal = 8.dp),
                                        ) {
                                            Text(
                                                "Auto-fill",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = BrandSkyPrimary,
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                shape = RoundedCornerShape(12.dp),
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = ::resetOtpRequest, enabled = !loading) {
                                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.edit_phone_number), color = BrandSkyPrimary)
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
                                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.resend_verification), color = BrandSkyPrimary)
                                }
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
                                        if (otp.isBlank()) {
                                            error = context.getString(R.string.otp_required_error)
                                            return@launch
                                        }
                                        container.authRepository.verifyOtp(
                                            normalizePhone(phone),
                                            otp.trim(),
                                            challengeId,
                                        )
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                        ),
                        contentPadding = PaddingValues(0.dp),
                        enabled = phone.isNotBlank() && (!otpRequested || otp.isNotBlank()) && !loading,
                    ) {
                        val buttonBrush = if (phone.isNotBlank() && (!otpRequested || otp.isNotBlank()) && !loading) {
                            Brush.horizontalGradient(
                                listOf(
                                    BrandSkyPrimary,
                                    BrandSkyVibrant,
                                    BrandSkyLight,
                                )
                            )
                        } else {
                            Brush.horizontalGradient(
                                listOf(
                                    Slate400,
                                    Slate400,
                                )
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(buttonBrush, shape = RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (loading) {
                                    CircularProgressIndicator(
                                        Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                }
                                Text(
                                    text = stringResource(if (otpRequested) R.string.verify_continue else R.string.continue_to_otp),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                                if (!loading) {
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Outlined.ArrowForward,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp),
                                    )
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
internal fun BrandMark(compact: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(if (compact) 32.dp else 44.dp)
                .background(
                    brush = Brush.linearGradient(
                        listOf(
                            BrandSkyPrimary,
                            BrandSkyVibrant,
                            BrandSkyLight,
                        )
                    ),
                    shape = RoundedCornerShape(if (compact) 10.dp else 14.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "N",
                color = Color.White,
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = stringResource(R.string.networkpeer),
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (!compact) {
                Text(
                    text = stringResource(R.string.brand_tagline),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
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
