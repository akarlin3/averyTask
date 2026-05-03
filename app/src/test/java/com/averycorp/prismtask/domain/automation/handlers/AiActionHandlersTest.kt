package com.averycorp.prismtask.domain.automation.handlers

import com.averycorp.prismtask.data.local.entity.AutomationRuleEntity
import com.averycorp.prismtask.data.preferences.UserPreferencesDataStore
import com.averycorp.prismtask.data.remote.api.AutomationCompleteRequest
import com.averycorp.prismtask.data.remote.api.AutomationCompleteResponse
import com.averycorp.prismtask.data.remote.api.AutomationSummarizeRequest
import com.averycorp.prismtask.data.remote.api.AutomationSummarizeResponse
import com.averycorp.prismtask.data.remote.api.PrismTaskApi
import com.averycorp.prismtask.domain.automation.ActionResult
import com.averycorp.prismtask.domain.automation.AutomationAction
import com.averycorp.prismtask.domain.automation.AutomationEvent
import com.averycorp.prismtask.domain.automation.EvaluationContext
import com.averycorp.prismtask.domain.automation.ExecutionContext
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * Tests for [AiCompleteActionHandler] and [AiSummarizeActionHandler].
 *
 * Scope:
 *  * Happy path returns [ActionResult.Ok]
 *  * Master AI toggle off short-circuits without calling the network
 *  * 451 from the backend maps to [ActionResult.Skipped]
 *  * Other HTTP failures + IO failures map to [ActionResult.Error]
 */
class AiActionHandlersTest {

    private val prefs: UserPreferencesDataStore = mockk(relaxed = true)
    private val api: PrismTaskApi = mockk()

    private val completeHandler = AiCompleteActionHandler(prefs, api)
    private val summarizeHandler = AiSummarizeActionHandler(prefs, api)

    private val sampleRule = AutomationRuleEntity(
        id = 42L,
        name = "Test rule",
        triggerJson = "{}",
        actionJson = "{}",
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun ctxFor(event: AutomationEvent): ExecutionContext = ExecutionContext(
        rule = sampleRule,
        event = event,
        evaluation = EvaluationContext(event = event),
        depth = 0,
        lineage = emptySet(),
        parentLogId = null
    )

    private fun httpException(code: Int): HttpException {
        val errorBody = "".toResponseBody("application/json".toMediaType())
        val response = Response.error<Any>(code, errorBody)
        return HttpException(response)
    }

    // ----------------------------------------------------------------------
    // ai.complete — happy path / gate / failure mapping
    // ----------------------------------------------------------------------

    @Test
    fun `ai_complete success returns Ok with backend text`() = runTest {
        every { prefs.isAiFeaturesEnabledBlocking() } returns true
        coEvery { api.automationComplete(any()) } returns AutomationCompleteResponse(
            text = "Send the wrap-up email this afternoon."
        )

        val action = AutomationAction.AiComplete(prompt = "Suggest a follow-up")
        val result = completeHandler.execute(
            action,
            ctxFor(AutomationEvent.TaskCompleted(taskId = 7L, occurredAt = 1234L))
        )

        assertTrue("expected Ok but got $result", result is ActionResult.Ok)
        result as ActionResult.Ok
        assertEquals("ai.complete", result.type)
        assertTrue(
            "message should include AI text, was: ${result.message}",
            result.message?.contains("Send the wrap-up email") == true
        )
    }

    @Test
    fun `ai_complete forwards prompt and event context to api`() = runTest {
        every { prefs.isAiFeaturesEnabledBlocking() } returns true
        coEvery { api.automationComplete(any()) } returns AutomationCompleteResponse(text = "ok")

        val action = AutomationAction.AiComplete(prompt = "What next?", targetField = "title")
        val event = AutomationEvent.TaskCompleted(taskId = 99L, occurredAt = 555L)
        completeHandler.execute(action, ctxFor(event))

        coVerify {
            api.automationComplete(
                match<AutomationCompleteRequest> { req ->
                    req.prompt == "What next?" &&
                        req.context?.get("trigger_kind") == "TaskCompleted" &&
                        req.context?.get("task_id") == 99L &&
                        req.context?.get("target_field") == "title" &&
                        req.context?.get("rule_id") == 42L
                }
            )
        }
    }

    @Test
    fun `ai_complete short-circuits with Skipped when master AI toggle is off`() = runTest {
        every { prefs.isAiFeaturesEnabledBlocking() } returns false

        val action = AutomationAction.AiComplete(prompt = "Anything")
        val result = completeHandler.execute(
            action,
            ctxFor(AutomationEvent.TaskCompleted(taskId = 1L))
        )

        assertTrue(result is ActionResult.Skipped)
        coVerify(exactly = 0) { api.automationComplete(any()) }
    }

    @Test
    fun `ai_complete maps 451 to Skipped`() = runTest {
        every { prefs.isAiFeaturesEnabledBlocking() } returns true
        coEvery { api.automationComplete(any()) } throws httpException(451)

        val action = AutomationAction.AiComplete(prompt = "Anything")
        val result = completeHandler.execute(
            action,
            ctxFor(AutomationEvent.TaskCompleted(taskId = 1L))
        )

        assertTrue("expected Skipped but got $result", result is ActionResult.Skipped)
        result as ActionResult.Skipped
        assertTrue(result.reason.contains("451"))
    }

    @Test
    fun `ai_complete maps 500 to Error`() = runTest {
        every { prefs.isAiFeaturesEnabledBlocking() } returns true
        coEvery { api.automationComplete(any()) } throws httpException(500)

        val result = completeHandler.execute(
            AutomationAction.AiComplete(prompt = "Anything"),
            ctxFor(AutomationEvent.TaskCompleted(taskId = 1L))
        )

        assertTrue(result is ActionResult.Error)
    }

    @Test
    fun `ai_complete maps generic IOException to Error`() = runTest {
        every { prefs.isAiFeaturesEnabledBlocking() } returns true
        coEvery { api.automationComplete(any()) } throws java.io.IOException("offline")

        val result = completeHandler.execute(
            AutomationAction.AiComplete(prompt = "Anything"),
            ctxFor(AutomationEvent.TaskCompleted(taskId = 1L))
        )

        assertTrue(result is ActionResult.Error)
    }

    @Test
    fun `ai_complete returns Error when action shape is wrong`() = runTest {
        every { prefs.isAiFeaturesEnabledBlocking() } returns true

        val result = completeHandler.execute(
            AutomationAction.AiSummarize(scope = "today"),
            ctxFor(AutomationEvent.TaskCompleted(taskId = 1L))
        )

        assertTrue(result is ActionResult.Error)
        coVerify(exactly = 0) { api.automationComplete(any()) }
    }

    // ----------------------------------------------------------------------
    // ai.summarize — happy path / gate / failure mapping
    // ----------------------------------------------------------------------

    @Test
    fun `ai_summarize success returns Ok with backend summary`() = runTest {
        every { prefs.isAiFeaturesEnabledBlocking() } returns true
        coEvery { api.automationSummarize(any()) } returns AutomationSummarizeResponse(
            summary = "You finished 4 tasks today."
        )

        val action = AutomationAction.AiSummarize(scope = "today", maxItems = 25)
        val result = summarizeHandler.execute(
            action,
            ctxFor(AutomationEvent.TimeTick(hour = 9, minute = 0))
        )

        assertTrue("expected Ok but got $result", result is ActionResult.Ok)
        result as ActionResult.Ok
        assertTrue(result.message?.contains("You finished 4 tasks") == true)
    }

    @Test
    fun `ai_summarize forwards scope and max_items to api`() = runTest {
        every { prefs.isAiFeaturesEnabledBlocking() } returns true
        coEvery { api.automationSummarize(any()) } returns AutomationSummarizeResponse(summary = "ok")

        val action = AutomationAction.AiSummarize(scope = "week", maxItems = 7)
        summarizeHandler.execute(
            action,
            ctxFor(AutomationEvent.TimeTick(hour = 9, minute = 0, occurredAt = 100L))
        )

        coVerify {
            api.automationSummarize(
                match<AutomationSummarizeRequest> { req ->
                    req.scope == "week" &&
                        req.maxItems == 7 &&
                        req.context?.get("trigger_kind") == "TimeTick" &&
                        req.context?.get("hour") == 9 &&
                        req.context?.get("minute") == 0
                }
            )
        }
    }

    @Test
    fun `ai_summarize short-circuits with Skipped when master AI toggle is off`() = runTest {
        every { prefs.isAiFeaturesEnabledBlocking() } returns false

        val result = summarizeHandler.execute(
            AutomationAction.AiSummarize(scope = "today"),
            ctxFor(AutomationEvent.TimeTick(hour = 9, minute = 0))
        )

        assertTrue(result is ActionResult.Skipped)
        coVerify(exactly = 0) { api.automationSummarize(any()) }
    }

    @Test
    fun `ai_summarize maps 451 to Skipped`() = runTest {
        every { prefs.isAiFeaturesEnabledBlocking() } returns true
        coEvery { api.automationSummarize(any()) } throws httpException(451)

        val result = summarizeHandler.execute(
            AutomationAction.AiSummarize(scope = "today"),
            ctxFor(AutomationEvent.TimeTick(hour = 9, minute = 0))
        )

        assertTrue("expected Skipped but got $result", result is ActionResult.Skipped)
    }

    @Test
    fun `ai_summarize maps 503 to Error`() = runTest {
        every { prefs.isAiFeaturesEnabledBlocking() } returns true
        coEvery { api.automationSummarize(any()) } throws httpException(503)

        val result = summarizeHandler.execute(
            AutomationAction.AiSummarize(scope = "today"),
            ctxFor(AutomationEvent.TimeTick(hour = 9, minute = 0))
        )

        assertTrue(result is ActionResult.Error)
    }

    @Test
    fun `ai_summarize returns Error when action shape is wrong`() = runTest {
        every { prefs.isAiFeaturesEnabledBlocking() } returns true

        val result = summarizeHandler.execute(
            AutomationAction.AiComplete(prompt = "x"),
            ctxFor(AutomationEvent.TimeTick(hour = 9, minute = 0))
        )

        assertTrue(result is ActionResult.Error)
        coVerify(exactly = 0) { api.automationSummarize(any()) }
    }
}
