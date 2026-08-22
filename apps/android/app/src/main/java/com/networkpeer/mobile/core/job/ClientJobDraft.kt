package com.networkpeer.mobile.core.job

import com.networkpeer.mobile.core.model.Point
import com.networkpeer.mobile.core.network.CreateJobBody
import com.networkpeer.mobile.core.network.CreateSubtaskBody
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ClientJobSubtaskDraft(
    val title: String = "",
    val description: String = "",
    val isRequired: Boolean = true,
)

@Serializable
data class ClientJobDraft(
    val title: String = "",
    val description: String = "",
    val category: String = "",
    val budgetCents: String = "",
    val currency: String = "USD",
    val latitude: String = "",
    val longitude: String = "",
    val address: String = "",
    val scheduledAt: String = "",
    val publicTitle: String = "",
    val publicDescription: String = "",
    val subtasks: List<ClientJobSubtaskDraft> = emptyList(),
)

enum class ClientJobDraftField {
    TITLE,
    DESCRIPTION,
    CATEGORY,
    BUDGET,
    CURRENCY,
    LATITUDE,
    LONGITUDE,
    ADDRESS,
    SCHEDULED_AT,
    PUBLIC_TITLE,
    PUBLIC_DESCRIPTION,
    SUBTASK,
}

enum class ClientJobDraftProblem {
    REQUIRED,
    INVALID_AMOUNT,
    INVALID_CURRENCY,
    INVALID_LATITUDE,
    INVALID_LONGITUDE,
    INVALID_TIMESTAMP,
    INVALID_LENGTH,
}

data class ClientJobDraftIssue(
    val field: ClientJobDraftField,
    val problem: ClientJobDraftProblem,
    val subtaskIndex: Int? = null,
)

object ClientJobDraftValidator {
    private val currencyPattern = Regex("^[A-Za-z]{3}$")

    fun validate(draft: ClientJobDraft): List<ClientJobDraftIssue> = buildList {
        validateRequiredText(draft.title, 3, 255, ClientJobDraftField.TITLE, this)
        validateRequiredText(draft.description, 10, 10_000, ClientJobDraftField.DESCRIPTION, this)
        validateRequiredText(draft.category, 2, 100, ClientJobDraftField.CATEGORY, this)

        val budget = draft.budgetCents.trim().toLongOrNull()
        if (budget == null || budget <= 0L || budget > MAX_BUDGET_CENTS) {
            add(ClientJobDraftIssue(ClientJobDraftField.BUDGET, ClientJobDraftProblem.INVALID_AMOUNT))
        }
        if (!currencyPattern.matches(draft.currency.trim().uppercase())) {
            add(ClientJobDraftIssue(ClientJobDraftField.CURRENCY, ClientJobDraftProblem.INVALID_CURRENCY))
        }

        val latitude = draft.latitude.trim().toDoubleOrNull()
        if (latitude == null || !latitude.isFinite() || latitude !in -90.0..90.0) {
            add(ClientJobDraftIssue(ClientJobDraftField.LATITUDE, ClientJobDraftProblem.INVALID_LATITUDE))
        }
        val longitude = draft.longitude.trim().toDoubleOrNull()
        if (longitude == null || !longitude.isFinite() || longitude !in -180.0..180.0) {
            add(ClientJobDraftIssue(ClientJobDraftField.LONGITUDE, ClientJobDraftProblem.INVALID_LONGITUDE))
        }

        if (draft.scheduledAt.isNotBlank() && !isIsoInstant(draft.scheduledAt.trim())) {
            add(ClientJobDraftIssue(ClientJobDraftField.SCHEDULED_AT, ClientJobDraftProblem.INVALID_TIMESTAMP))
        }
        validateOptionalText(draft.address, 500, ClientJobDraftField.ADDRESS, this)
        if (draft.publicTitle.isNotBlank()) {
            validateRequiredText(draft.publicTitle, 3, 255, ClientJobDraftField.PUBLIC_TITLE, this)
        }
        validateOptionalText(draft.publicDescription, 2_000, ClientJobDraftField.PUBLIC_DESCRIPTION, this)
        if (draft.subtasks.size > MAX_SUBTASKS) {
            add(ClientJobDraftIssue(ClientJobDraftField.SUBTASK, ClientJobDraftProblem.INVALID_LENGTH))
        }
        draft.subtasks.forEachIndexed { index, subtask ->
            validateRequiredText(subtask.title, 1, 255, ClientJobDraftField.SUBTASK, this, index)
            if (subtask.description.length > 2_000) {
                add(ClientJobDraftIssue(ClientJobDraftField.SUBTASK, ClientJobDraftProblem.INVALID_LENGTH, index))
            }
        }
    }

    fun createBody(draft: ClientJobDraft, idempotencyKey: String): CreateJobBody {
        require(validate(draft).isEmpty()) { "Attempted to create a job from an invalid draft." }
        return CreateJobBody(
            title = draft.title.trim(),
            description = draft.description.trim(),
            category = draft.category.trim(),
            budget_cents = draft.budgetCents.trim().toLong(),
            currency = draft.currency.trim().uppercase(),
            location = Point.fromLatitudeLongitude(
                latitude = draft.latitude.trim().toDouble(),
                longitude = draft.longitude.trim().toDouble(),
            ),
            address = draft.address.trim().ifBlank { null },
            scheduled_at = draft.scheduledAt.trim().ifBlank { null },
            public_title = draft.publicTitle.trim().ifBlank { null },
            public_description = draft.publicDescription.trim().ifBlank { null },
            idempotency_key = idempotencyKey,
            subtasks = draft.subtasks.map { subtask ->
                CreateSubtaskBody(
                    title = subtask.title.trim(),
                    description = subtask.description.trim().ifBlank { null },
                    is_required = subtask.isRequired,
                )
            },
        )
    }

    fun fingerprint(draft: ClientJobDraft): String = json.encodeToString(draft)

    private fun isIsoInstant(value: String): Boolean = runCatching {
        java.time.Instant.parse(value)
    }.isSuccess

    private fun validateRequiredText(
        value: String,
        minimum: Int,
        maximum: Int,
        field: ClientJobDraftField,
        issues: MutableList<ClientJobDraftIssue>,
        subtaskIndex: Int? = null,
    ) {
        val length = value.trim().length
        when {
            length == 0 -> issues += ClientJobDraftIssue(field, ClientJobDraftProblem.REQUIRED, subtaskIndex)
            length !in minimum..maximum -> issues += ClientJobDraftIssue(field, ClientJobDraftProblem.INVALID_LENGTH, subtaskIndex)
        }
    }

    private fun validateOptionalText(
        value: String,
        maximum: Int,
        field: ClientJobDraftField,
        issues: MutableList<ClientJobDraftIssue>,
    ) {
        if (value.trim().length > maximum) issues += ClientJobDraftIssue(field, ClientJobDraftProblem.INVALID_LENGTH)
    }

    private val json = Json { encodeDefaults = true }
    private const val MAX_BUDGET_CENTS = 1_000_000_000L
    private const val MAX_SUBTASKS = 50
}
