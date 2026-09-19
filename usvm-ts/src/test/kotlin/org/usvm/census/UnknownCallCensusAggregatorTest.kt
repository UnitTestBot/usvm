package org.usvm.census

import kotlin.test.Test
import kotlin.test.assertEquals

class UnknownCallCensusAggregatorTest {
    @Test
    fun `summary keeps repeated events separate from stable sites and preserves failures`() {
        val rawRecords = sequenceOf(
            unknownCallRecord(siteId = "project:file:fn:1:1:1:8", failureReason = "ANY_RECEIVER"),
            unknownCallRecord(siteId = "project:file:fn:1:1:1:8", failureReason = "ANY_RECEIVER"),
            unknownCallRecord(siteId = "project:file:fn:2:1:2:8", failureReason = "METHOD_BODY_UNAVAILABLE"),
            methodRecord(functionId = "project:file:fn", status = "completed"),
            methodRecord(functionId = "project:file:timeout", status = "timeout", error = "Machine timeout reached"),
            methodRecord(functionId = "project:file:error", status = "tool_error", error = "frontend failed"),
            projectRecord(projectId = "project", status = "completed"),
            projectRecord(projectId = "broken", status = "tool_error", error = "checkout missing"),
        )

        val summary = UnknownCallCensusAggregator.summarize(rawRecords)

        val profile = requireNotNull(summary.profiles["EMPTY_FRESH"])
        assertEquals(2, profile.projects)
        assertEquals(3, profile.functionsAnalyzed)
        assertEquals(1, profile.functionsCompleted)
        assertEquals(1, profile.functionsWithUnknownCalls)
        assertEquals(2, profile.uniqueSites)
        assertEquals(3, profile.rawEvents)
        assertEquals(mapOf("ANY_RECEIVER" to 2, "METHOD_BODY_UNAVAILABLE" to 1), profile.eventsByFailureReason)
        assertEquals(mapOf("RESIDUAL_FALLBACK:FRESH_SYMBOLIC_RETURN" to 3), profile.eventsByDecision)
        assertEquals(mapOf("external:unknown" to 3), profile.eventsByCallee)
        assertEquals(mapOf("external:unknown" to 2), profile.uniqueSitesByCallee)
        assertEquals(listOf("project:file:timeout"), profile.timeouts.mapNotNull { it.functionId })
        assertEquals(2, profile.errors.size)
    }

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

    private fun methodRecord(functionId: String, status: String, error: String? = null): String {
        val errorField = error?.let { ",\"error\":\"$it\"" }.orEmpty()
        return """
            {
              "kind":"method_result",
              "schemaVersion":1,
              "projectId":"project",
              "projectRevision":"0000000000000000000000000000000000000000",
              "profile":"EMPTY_FRESH",
              "functionId":"$functionId",
              "status":"$status"$errorField
            }
        """.trimIndent()
    }

    private fun projectRecord(projectId: String, status: String, error: String? = null): String {
        val errorField = error?.let { ",\"error\":\"$it\"" }.orEmpty()
        return """
            {
              "kind":"project_result",
              "schemaVersion":1,
              "projectId":"$projectId",
              "projectRevision":"0000000000000000000000000000000000000000",
              "profile":"EMPTY_FRESH",
              "status":"$status"$errorField
            }
        """.trimIndent()
    }
}
