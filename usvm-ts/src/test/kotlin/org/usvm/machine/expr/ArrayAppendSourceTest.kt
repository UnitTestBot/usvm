package org.usvm.machine.expr

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.usvm.api.TsTestValue
import org.usvm.machine.TsAnalysisStopReason
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.TsRuntimeFeatureLimitationEvent
import org.usvm.machine.TsRuntimeFeatureLimitationReason
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.getResourcePath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArrayAppendSourceTest : TsMethodTestRunner() {
    override val scene = EtsScene(
        projectFiles = listOf(
            loadEtsFileAutoConvert(
                getResourcePath("/models/ArrayAppendSource.ts"),
                provider = EtsIrProvider.TS_FRONTEND,
            ),
        ),
    )
    override val tsOptions = TsOptions(maxArraySize = 3)

    @Test
    fun `dense appends preserve length payload kinds and aliases`() {
        listOf("appendToEmpty", "appendThroughMixedAlias", "overwriteAtCapacity", "appendAfterTruncate")
            .forEach { methodName ->
                discoverProperties<TsTestValue.TsNumber>(
                    method = getMethod(methodName = methodName, className = "ArrayAppendSource"),
                    { result -> result.number == 1.0 },
                    invariants = arrayOf({ result -> result.number == 1.0 }),
                )
            }
    }

    @Test
    fun `capacity and sparse growth remain explicit limitations`() {
        val boundaries = mapOf(
            "appendBeyondCapacity" to TsRuntimeFeatureLimitationReason.ARRAY_LENGTH_CAPACITY,
            "sparseGrowth" to TsRuntimeFeatureLimitationReason.ARRAY_INDEX_GROWTH,
        )

        for ((methodName, expectedReason) in boundaries) {
            val method = getMethod(methodName = methodName, className = "ArrayAppendSource")
            val reasons = mutableListOf<TsRuntimeFeatureLimitationReason>()
            val observer = object : TsInterpreterObserver {
                override fun onRuntimeFeatureLimitation(event: TsRuntimeFeatureLimitationEvent) {
                    reasons += event.reason
                }
            }

            TsMachine(scene = scene, options = options, tsOptions = tsOptions, observer = observer).use { machine ->
                val outcome = machine.analyzeWithOutcome(methods = listOf(method))

                assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason, methodName)
                assertTrue(outcome.states.isEmpty(), methodName)
                assertEquals(listOf(expectedReason), reasons, methodName)
            }
        }
    }
}
