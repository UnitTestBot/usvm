package org.usvm.machine.expr

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.usvm.UMachineOptions
import org.usvm.api.TsTestValue
import org.usvm.machine.TsAnalysisStopReason
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.TsRuntimeFeatureLimitationEvent
import org.usvm.machine.TsRuntimeFeatureLimitationReason
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.TsTestResolver
import org.usvm.util.getResourcePath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RuntimeLimitationBoundariesTest : TsMethodTestRunner() {
    override val scene = EtsScene(
        listOf(
            loadEtsFileAutoConvert(
                getResourcePath("/models/RuntimeLimitationBoundaries.ts"),
                provider = EtsIrProvider.TS_FRONTEND,
            ),
        ),
    )

    @Test
    fun `regular expression limitation terminates once with its exact reason`() {
        val observer = RecordingObserver()
        val target = method("regularExpressionLiteral")

        TsMachine(
            scene = scene,
            options = UMachineOptions(throwExceptionOnStepFailure = true, stepsFromLastCovered = 128L),
            tsOptions = TsOptions(),
            observer = observer,
        ).use { machine ->
            val outcome = machine.analyzeWithOutcome(listOf(target))

            assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason)
            assertTrue(outcome.states.isEmpty())
            assertTrue(outcome.unsupportedPaths.any { "REGULAR_EXPRESSION_LITERAL" in it })
        }
        assertEquals(listOf(TsRuntimeFeatureLimitationReason.REGULAR_EXPRESSION_LITERAL), observer.reasons)
    }

    @Test
    fun `unsupported storage terminates while a supported sibling still returns`() {
        val observer = RecordingObserver()
        val target = method("unknownStorageOrSupportedSibling")

        TsMachine(
            scene = scene,
            options = UMachineOptions(throwExceptionOnStepFailure = true, stepsFromLastCovered = 128L),
            tsOptions = TsOptions(),
            observer = observer,
        ).use { machine ->
            val outcome = machine.analyzeWithOutcome(listOf(target))
            val values = outcome.states.map { state -> TsTestResolver().resolve(target, state).returnValue }

            assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason)
            assertTrue(outcome.unsupportedPaths.any { "ARRAY_STORAGE_TYPE" in it })
            assertTrue(values.any { value -> value is TsTestValue.TsNumber && value.number == 7.0 })
        }
        assertTrue(observer.reasons.isNotEmpty())
        assertTrue(observer.reasons.all { reason -> reason == TsRuntimeFeatureLimitationReason.ARRAY_STORAGE_TYPE })
        assertTrue(observer.reasons.size < 128)
    }

    @Test
    fun `ordinary object length is read as its actual field`() {
        discoverProperties<TsTestValue.TsNumber>(
            method = method("ordinaryLengthField"),
            { result -> result.number == 17.0 },
            invariants = arrayOf({ result -> result.number == 17.0 }),
        )
    }

    private fun method(name: String): EtsMethod = getMethod(
        methodName = name,
        className = "RuntimeLimitationBoundaries",
    )

    private class RecordingObserver : TsInterpreterObserver {
        val reasons = mutableListOf<TsRuntimeFeatureLimitationReason>()

        override fun onRuntimeFeatureLimitation(event: TsRuntimeFeatureLimitationEvent) {
            reasons += event.reason
        }
    }
}
