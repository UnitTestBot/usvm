package org.usvm.machine

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.PathSelectionStrategy
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.TsTestResolver
import org.usvm.util.getResourcePath
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TsStringConcatLimitationTest : TsMethodTestRunner() {
    private val source = getResourcePath("/models/StringConcatLimitation.ts")
    private val sourceFile = loadEtsFileAutoConvert(
        source,
        provider = EtsIrProvider.TS_FRONTEND,
    )
    override val scene = EtsScene(listOf(sourceFile))
    override val tsOptions = TsOptions()

    private val limitations = mutableListOf<TsRuntimeFeatureLimitationEvent>()
    private var outcome: TsAnalysisResult? = null

    override val runner = { method: EtsMethod, machineOptions: UMachineOptions ->
        limitations.clear()
        val observer = object : TsInterpreterObserver {
            override fun onRuntimeFeatureLimitation(event: TsRuntimeFeatureLimitationEvent) {
                limitations += event
            }
        }
        val analysis = TsMachine(scene = scene, options = machineOptions, tsOptions = tsOptions, observer = observer)
            .use { machine -> machine.analyzeWithOutcome(listOf(method)) }
        outcome = analysis

        analysis.states.map { state -> TsTestResolver().resolve(method, state) }
    }

    init {
        options = options.copy(
            pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            stopOnCoverage = 0,
            throwExceptionOnStepFailure = true,
        )
    }

    @Test
    fun `unsupported concatenation preserves the other source branch`() {
        val method = getMethod(methodName = "sibling", className = "StringConcatLimitation")

        discoverProperties<TsTestValue.TsBoolean, TsTestValue, TsTestValue.TsString>(
            method = method,
            { flag, _, result -> flag.value && result.value == "ok" },
            { flag, _, result -> !flag.value && result.value == "skip" },
            invariants = arrayOf({ flag, _, result -> result.value == if (flag.value) "ok" else "skip" }),
        )

        assertUnsupportedConversion()
    }

    @Test
    fun `number-narrowed any reports a limitation without reading the invalidated scope`() {
        val method = getMethod(methodName = "negativeOnly", className = "StringConcatLimitation")

        val tests = runner(method, options)

        assertTrue(tests.isNotEmpty())
        assertTrue(tests.all { (it.returnValue as? TsTestValue.TsString)?.value == "skip" })
        assertUnsupportedConversion()
    }

    private fun assertUnsupportedConversion() {
        val analysis = requireNotNull(outcome)
        assertEquals(TsAnalysisStopReason.EXHAUSTED, analysis.stopReason)
        assertTrue(analysis.unsupportedPaths.any { "Unsupported string concatenation operand" in it })
        assertTrue(limitations.isNotEmpty())
        assertTrue(limitations.all { it.reason == TsRuntimeFeatureLimitationReason.STRING_CONCAT_OPERAND_CONVERSION })
        assertTrue(limitations.all { "+" in it.statement.toString() })
    }
}
