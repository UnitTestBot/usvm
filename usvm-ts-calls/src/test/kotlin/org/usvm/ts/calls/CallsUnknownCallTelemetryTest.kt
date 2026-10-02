package org.usvm.ts.calls

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.usvm.machine.call.TsResidualCallPolicy
import org.usvm.machine.call.TsUnknownCallDecision
import org.usvm.machine.call.TsUnknownCallEvent
import org.usvm.machine.call.TsUnknownCallFailureReason
import org.usvm.machine.call.TsUnknownCallOutcome
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CallsUnknownCallTelemetryTest {
    @Test
    fun `sink converts unknown call events into ordered serializable cell records`() {
        val source = resourcePath("/calls/SourceTargetReplayFixture.ts")
        val file = loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)
        val method = file.allClasses.flatMap { cls -> cls.methods }
            .single { candidate -> candidate.name == "completesReturnExpression" }
        val callSite = method.cfg.stmts.first { statement -> statement.location.origin != null }
        val cell = CallsExperimentCellIdentity(
            experimentId = "experiment",
            projectId = "project",
            revision = "revision",
            development = true,
            functionId = "function",
            targetId = "target",
            siteId = "site",
            targetMode = CallsSourceTargetMode.COMPLETED_RETURN,
            profile = CallsExperimentProfile.FROZEN_STOP,
            seed = 17L,
        )
        val records = mutableListOf<CallsUnknownCallRecord>()
        val sink = callsUnknownCallEventSink(cell = cell, appendAndFlush = records::add)

        sink(
            TsUnknownCallEvent(
                callSite = callSite,
                callee = method.signature,
                failureReason = TsUnknownCallFailureReason.PARTIAL_APPROXIMATION,
                decision = TsUnknownCallDecision.ModelApplied(modelId = "ts.array.isArray"),
            )
        )
        sink(
            TsUnknownCallEvent(
                callSite = callSite,
                callee = method.signature,
                failureReason = TsUnknownCallFailureReason.METHOD_BODY_UNAVAILABLE,
                decision = TsUnknownCallDecision.ResidualFallback(TsResidualCallPolicy.STOP_PATH),
            )
        )

        assertEquals(listOf(1, 2), records.map { record -> record.eventIndex })
        val modeled = records.first()
        assertEquals(cell, modeled.cell)
        assertEquals(source.fileName.toString(), modeled.callSite.sourcePath)
        assertNotNull(modeled.callSite.start)
        assertEquals("PARTIAL_APPROXIMATION", modeled.failureReason)
        assertEquals(CallsUnknownCallDecisionKind.MODEL_APPLIED, modeled.decision)
        assertEquals(TsUnknownCallOutcome.MODEL_APPLIED.name, modeled.outcome)
        assertEquals("ts.array.isArray", modeled.modelId)
        assertNull(modeled.residualPolicy)

        val residual = records.last()
        assertEquals(CallsUnknownCallDecisionKind.RESIDUAL_FALLBACK, residual.decision)
        assertEquals(TsUnknownCallOutcome.PATH_STOPPED.name, residual.outcome)
        assertNull(residual.modelId)
        assertEquals(TsResidualCallPolicy.STOP_PATH.name, residual.residualPolicy)

        val encoded = CallsExperimentJson.json.encodeToString<CallsRawRecord>(modeled)
        val decoded = CallsExperimentJson.json.decodeFromString<CallsRawRecord>(encoded)
        assertEquals(modeled, assertIs<CallsUnknownCallRecord>(decoded))
    }

    private fun resourcePath(name: String): Path {
        val resource = checkNotNull(javaClass.getResource(name)) { "Missing test resource: $name" }
        return Path.of(resource.toURI())
    }
}
