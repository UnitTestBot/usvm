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

class ArrayIteratorSourceTest : TsMethodTestRunner() {
    override val scene = EtsScene(
        projectFiles = listOf(
            loadEtsFileAutoConvert(
                getResourcePath("/models/ArrayIteratorSource.ts"),
                provider = EtsIrProvider.TS_FRONTEND,
            ),
        ),
    )
    override val tsOptions = TsOptions(maxArraySize = 6)

    @Test
    fun `native array iteration preserves values evaluation order and live length`() {
        val methods = listOf(
            "literalSpread", "destructure", "preserveKindsAndAliases", "evaluationOrder",
            "liveLength", "shortenedLength", "exhaustedDestructuring",
            "reversedMixedAlias", "unshiftedMixedAlias", "shiftedMixedAlias",
            "localSymbolShadow",
        )

        for (methodName in methods) {
            discoverProperties<TsTestValue.TsNumber>(
                method = getMethod(methodName = methodName, className = "ArrayIteratorSource"),
                { result -> result.number == 1.0 },
                invariants = arrayOf({ result -> result.number == 1.0 }),
            )
        }
    }

    @Test
    fun `symbolic array inputs retain both feasible branches`() {
        discoverProperties<TsTestValue.TsArray<TsTestValue.TsNumber>, TsTestValue.TsNumber>(
            method = getMethod(methodName = "symbolicInput", className = "ArrayIteratorSource"),
            { _, result -> result.number == 0.0 },
            { _, result -> result.number == 1.0 },
            invariants = arrayOf({ _, result -> result.number == 0.0 || result.number == 1.0 }),
        )
    }

    @Test
    fun `custom iterables retain explicit limitations`() {
        val methods = listOf("stringNamedLookalike")
        for (methodName in methods) {
            val method = getMethod(methodName = methodName, className = "ArrayIteratorSource")
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
                assertEquals(listOf(TsRuntimeFeatureLimitationReason.ARRAY_ITERATOR_UNSUPPORTED), reasons, methodName)
            }
        }
    }

    @Test
    fun `own symbol overrides cannot silently use the native array iterator`() {
        val methodNames = listOf(
            "customSymbolOverride",
            "builtinSymbolKey",
            "aliasedSymbolOverride",
            "bracketSymbolOverride",
        )

        for (methodName in methodNames) {
            val method = getMethod(methodName = methodName, className = "ArrayIteratorSource")

            TsMachine(scene = scene, options = options, tsOptions = tsOptions).use { machine ->
                val outcome = machine.analyzeWithOutcome(methods = listOf(method))

                assertEquals(TsAnalysisStopReason.EXHAUSTED, outcome.stopReason, methodName)
                assertTrue(outcome.states.isEmpty(), methodName)
                assertTrue(outcome.unsupportedPaths.isNotEmpty(), "$methodName: $outcome")
            }
        }
    }
}
