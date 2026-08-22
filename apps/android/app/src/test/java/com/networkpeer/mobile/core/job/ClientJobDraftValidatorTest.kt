package com.networkpeer.mobile.core.job

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientJobDraftValidatorTest {
    @Test
    fun `valid draft produces exact geojson coordinate order and optional checklist`() {
        val draft = validDraft().copy(
            latitude = "37.7749",
            longitude = "-122.4194",
            subtasks = listOf(ClientJobSubtaskDraft(title = "Photograph the completed repair", isRequired = false)),
        )

        val body = ClientJobDraftValidator.createBody(draft, "request-key")

        assertEquals(listOf(-122.4194, 37.7749), body.location.coordinates)
        assertEquals("USD", body.currency)
        assertEquals(false, body.subtasks.single().is_required)
        assertEquals("request-key", body.idempotency_key)
    }

    @Test
    fun `invalid draft reports location budget and checklist issues`() {
        val issues = ClientJobDraftValidator.validate(
            validDraft().copy(
                budgetCents = "0",
                latitude = "91",
                longitude = "not-a-number",
                subtasks = listOf(ClientJobSubtaskDraft()),
            ),
        )

        assertTrue(issues.any { it.field == ClientJobDraftField.BUDGET && it.problem == ClientJobDraftProblem.INVALID_AMOUNT })
        assertTrue(issues.any { it.field == ClientJobDraftField.LATITUDE && it.problem == ClientJobDraftProblem.INVALID_LATITUDE })
        assertTrue(issues.any { it.field == ClientJobDraftField.LONGITUDE && it.problem == ClientJobDraftProblem.INVALID_LONGITUDE })
        assertTrue(issues.any { it.field == ClientJobDraftField.SUBTASK && it.subtaskIndex == 0 })
    }

    @Test
    fun `draft fingerprint changes when request metadata changes`() {
        val first = validDraft()
        val second = first.copy(address = "New address")

        assertTrue(ClientJobDraftValidator.fingerprint(first) != ClientJobDraftValidator.fingerprint(second))
    }

    @Test
    fun `server text and budget limits are rejected before a request is sent`() {
        val issues = ClientJobDraftValidator.validate(
            validDraft().copy(
                title = "No",
                description = "Too short",
                budgetCents = "1000000001",
                publicTitle = "X",
            ),
        )

        assertTrue(issues.any { it.field == ClientJobDraftField.TITLE && it.problem == ClientJobDraftProblem.INVALID_LENGTH })
        assertTrue(issues.any { it.field == ClientJobDraftField.DESCRIPTION && it.problem == ClientJobDraftProblem.INVALID_LENGTH })
        assertTrue(issues.any { it.field == ClientJobDraftField.BUDGET && it.problem == ClientJobDraftProblem.INVALID_AMOUNT })
        assertTrue(issues.any { it.field == ClientJobDraftField.PUBLIC_TITLE && it.problem == ClientJobDraftProblem.INVALID_LENGTH })
    }

    private fun validDraft() = ClientJobDraft(
        title = "Replace door handle",
        description = "Bring a compatible replacement and photograph the installed handle.",
        category = "home_repair",
        budgetCents = "12500",
        latitude = "40.7128",
        longitude = "-74.0060",
    )
}
