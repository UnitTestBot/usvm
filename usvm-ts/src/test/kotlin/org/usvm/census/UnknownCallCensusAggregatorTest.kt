package org.usvm.census

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UnknownCallCensusAggregatorTest {
    @Test
    fun `summary keeps repeated events separate from stable sites and preserves failures`() {
        val rawRecords = sequenceOf(
            unknownCallRecord(siteId = "project:file:fn:1:1:1:8", failureReason = "ANY_RECEIVER"),
            unknownCallRecord(siteId = "project:file:fn:1:1:1:8", failureReason = "ANY_RECEIVER"),
            unknownCallRecord(siteId = "project:file:fn:2:1:2:8", failureReason = "METHOD_BODY_UNAVAILABLE"),
            methodRecord(functionId = "project:file:fn", status = "completed"),
            methodRecord(
                functionId = "project:file:partial",
                status = "partial",
                error = "step failed",
                failureCount = 2,
            ),
            methodRecord(functionId = "project:file:timeout", status = "timeout", error = "Machine timeout reached"),
            methodRecord(functionId = "project:file:error", status = "tool_error", error = "frontend failed"),
            projectRecord(projectId = "project", status = "completed"),
            projectRecord(projectId = "broken", status = "tool_error", error = "checkout missing"),
        )

        val summary = UnknownCallCensusAggregator.summarize(rawRecords)

        val profile = requireNotNull(summary.profiles["EMPTY_FRESH"])
        assertEquals(UnknownCallCensusCompleteness.PARTIAL, summary.input.completeness)
        assertContains(summary.input.issues, UnknownCallCensusInputIssue.MISSING_RUN_START)
        assertContains(summary.input.issues, UnknownCallCensusInputIssue.MISSING_RUN_RESULT)
        assertContains(summary.input.issues, UnknownCallCensusInputIssue.UNSUPPORTED_DATA_SCHEMA)
        assertEquals(2, profile.projects)
        assertEquals(4, profile.functionsAnalyzed)
        assertEquals(1, profile.functionsCompleted)
        assertEquals(1, profile.functionsPartial)
        assertEquals(1, profile.functionsWithUnknownCalls)
        assertEquals(2, profile.uniqueSites)
        assertEquals(3, profile.rawEvents)
        assertEquals(mapOf("ANY_RECEIVER" to 2, "METHOD_BODY_UNAVAILABLE" to 1), profile.eventsByFailureReason)
        assertEquals(mapOf("RESIDUAL_FALLBACK:FRESH_SYMBOLIC_RETURN" to 3), profile.eventsByDecision)
        assertEquals(mapOf("external:unknown" to 3), profile.eventsByCallee)
        assertEquals(mapOf("external:unknown" to 2), profile.uniqueSitesByCallee)
        assertEquals(listOf("project:file:partial"), profile.partials.mapNotNull { it.functionId })
        assertEquals(2, profile.partials.single().failureCount)
        assertEquals(listOf("project:file:timeout"), profile.timeouts.mapNotNull { it.functionId })
        assertEquals(2, profile.errors.size)
    }

    @Test
    fun `ordered supported run is complete despite method and project failures`() {
        val records = sequenceOf(
            runStart(),
            methodRecord(functionId = "project:file:partial", status = "partial"),
            projectRecord(projectId = "project", status = "completed", schemaVersion = 2),
            projectRecord(projectId = "broken", status = "tool_error", schemaVersion = 2),
            runResult(),
        )

        val summary = UnknownCallCensusAggregator.summarize(records)

        assertEquals(UnknownCallCensusCompleteness.COMPLETE, summary.input.completeness)
        assertEquals(emptyList(), summary.input.issues)
        val profile = requireNotNull(summary.profiles["EMPTY_FRESH"])
        assertEquals(1, profile.functionsPartial)
        assertEquals(1, profile.errors.size)
    }

    @Test
    fun `interrupted run retains counts and reports missing end`() {
        val records = sequenceOf(runStart(), methodRecord(functionId = "project:file:fn", status = "completed"))

        val summary = UnknownCallCensusAggregator.summarize(records)

        assertEquals(UnknownCallCensusCompleteness.PARTIAL, summary.input.completeness)
        assertContains(summary.input.issues, UnknownCallCensusInputIssue.MISSING_RUN_RESULT)
        assertEquals(1, summary.profiles.getValue("EMPTY_FRESH").functionsCompleted)
    }

    @Test
    fun `duplicate reversed unsupported and trailing markers cannot certify a complete run`() {
        val data = methodRecord(functionId = "project:file:fn", status = "completed")
        val cases = listOf(
            listOf(runStart(), data, runResult(), runResult()) to UnknownCallCensusInputIssue.DUPLICATE_RUN_RESULT,
            listOf(runResult(), runStart(), data) to UnknownCallCensusInputIssue.OUT_OF_ORDER_MARKERS,
            listOf(runStart(schemaVersion = 3), data, runResult()) to UnknownCallCensusInputIssue.INVALID_RUN_START,
            listOf("""{"kind":"run_start","schemaVersion":{}}""", data, runResult()) to
                UnknownCallCensusInputIssue.INVALID_RUN_START,
            listOf(runStart(), data, runResult(schemaVersion = 3)) to UnknownCallCensusInputIssue.INVALID_RUN_RESULT,
            listOf(runStart(), data, runResult(status = "failed")) to UnknownCallCensusInputIssue.INVALID_RUN_RESULT,
            listOf(runStart(), data, runResult().replace("2026-10-15T00:00:00Z", "2026-10-15T00:02:00Z")) to
                UnknownCallCensusInputIssue.INVALID_RUN_RESULT,
            listOf(runStart(), runResult(), data) to UnknownCallCensusInputIssue.DATA_OUTSIDE_RUN,
            listOf(runStart(), data, runStart(), runResult()) to UnknownCallCensusInputIssue.DUPLICATE_RUN_START,
            listOf(runStart(), data.replace("\"schemaVersion\":2", "\"schemaVersion\":3"), runResult()) to
                UnknownCallCensusInputIssue.UNSUPPORTED_DATA_SCHEMA,
        )

        cases.forEach { (records, expectedIssue) ->
            val summary = UnknownCallCensusAggregator.summarize(records.asSequence())

            assertEquals(UnknownCallCensusCompleteness.PARTIAL, summary.input.completeness)
            assertContains(summary.input.issues, expectedIssue)
            assertEquals(1, summary.profiles.getValue("EMPTY_FRESH").functionsCompleted)
        }
    }

    @Test
    fun `unsupported current method and project statuses report raw line and value`() {
        val cases = listOf(
            methodRecord(functionId = "project:file:fn", status = "future_method") to
                "method_result status 'future_method'",
            projectRecord(projectId = "project", status = "future_project") to "project_result status 'future_project'",
        )

        cases.forEach { (record, expectedMessage) ->
            val failure = assertFailsWith<IllegalArgumentException> {
                UnknownCallCensusAggregator.summarize(sequenceOf(runStart(), "", record, runResult()))
            }

            assertContains(failure.message.orEmpty(), "line 3")
            assertContains(failure.message.orEmpty(), expectedMessage)
        }
    }

    private fun runStart(schemaVersion: Int = 2): String =
        """{"kind":"run_start","schemaVersion":$schemaVersion,"startedAt":"2026-10-15T00:00:00Z"}"""

    private fun runResult(schemaVersion: Int = 2, status: String = "completed"): String =
        """{"kind":"run_result","schemaVersion":$schemaVersion,"startedAt":"2026-10-15T00:00:00Z",""" +
            """"finishedAt":"2026-10-15T00:01:00Z","status":"$status"}"""

    private fun unknownCallRecord(siteId: String, failureReason: String): String = """
        {
          "kind":"unknown_call",
          "schemaVersion":1,
          "projectId":"project",
          "projectRevision":"0000000000000000000000000000000000000000",
          "profile":"EMPTY_FRESH",
          "functionId":"project:file:fn",
          "siteId":"$siteId",
          "calleeId":"external:unknown",
          "failureReason":"$failureReason",
          "decision":"RESIDUAL_FALLBACK:FRESH_SYMBOLIC_RETURN"
        }
    """.trimIndent()

    private fun methodRecord(
        functionId: String,
        status: String,
        error: String? = null,
        failureCount: Int = 0,
    ): String {
        val errorField = error?.let { ",\"error\":\"$it\"" }.orEmpty()
        return """
            {
              "kind":"method_result",
              "schemaVersion":2,
              "projectId":"project",
              "projectRevision":"0000000000000000000000000000000000000000",
              "profile":"EMPTY_FRESH",
              "functionId":"$functionId",
              "status":"$status",
              "failureCount":$failureCount$errorField
            }
        """.trimIndent()
    }

    private fun projectRecord(
        projectId: String,
        status: String,
        error: String? = null,
        schemaVersion: Int = 1,
    ): String {
        val errorField = error?.let { ",\"error\":\"$it\"" }.orEmpty()
        return """
            {
              "kind":"project_result",
              "schemaVersion":$schemaVersion,
              "projectId":"$projectId",
              "projectRevision":"0000000000000000000000000000000000000000",
              "profile":"EMPTY_FRESH",
              "status":"$status"$errorField
            }
        """.trimIndent()
    }
}
