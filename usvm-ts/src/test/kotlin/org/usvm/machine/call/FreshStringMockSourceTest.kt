package org.usvm.machine.call

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.usvm.PathSelectionStrategy
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.TsTest
import org.usvm.api.TsTestValue
import org.usvm.machine.TsAnalysisStopReason
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.TsTestResolver
import org.usvm.util.getResourcePath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FreshStringMockSourceTest : TsMethodTestRunner() {
    private val sourceFile = loadEtsFileAutoConvert(
        getResourcePath("/models/FreshStringMock.ts"),
        provider = EtsIrProvider.TS_FRONTEND,
    )
    override val scene = EtsScene(projectFiles = listOf(sourceFile))
    override val tsOptions = TsOptions(
        maxArraySize = 8,
        unknownCallFallback = TsResidualCallPolicy.FRESH_SYMBOLIC_RETURN,
    )
    override val runner: (EtsMethod, UMachineOptions) -> List<TsTest> = { method, machineOptions ->
        TsMachine(scene = scene, options = machineOptions, tsOptions = tsOptions).use { machine ->
            val analysis = machine.analyzeWithOutcome(methods = listOf(method))

            assertEquals(TsAnalysisStopReason.EXHAUSTED, analysis.stopReason)
            assertTrue(analysis.unsupportedPaths.isEmpty(), "${method.name}: ${analysis.unsupportedPaths}")
            analysis.states.map { state -> TsTestResolver().resolve(method, state) }
        }
    }

    init {
        options = options.copy(
            pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            stopOnCoverage = 0,
            throwExceptionOnStepFailure = true,
            stepsFromLastCovered = 128L,
        )
    }

    @Test
    fun `fresh string result has a readable private length`() {
        discoverProperties<TsTestValue.TsNumber>(
            method = method("length"),
            { result -> result.number in 0.0..8.0 },
            invariants = arrayOf({ result -> result.number in 0.0..8.0 }),
        )
    }

    @Test
    fun `fresh string result retains backing through ordinary concatenation`() {
        discoverProperties<TsTestValue.TsString>(
            method = method("concat"),
            { result -> result.value.endsWith("!") },
            invariants = arrayOf({ result -> result.value.endsWith("!") }),
        )
    }

    @Test
    fun `fresh strings retain both empty and nonempty source branches`() {
        discoverProperties<TsTestValue.TsNumber>(
            method = method("branchOnLength"),
            { result -> result.number == 0.0 },
            { result -> result.number == 1.0 },
            invariants = arrayOf({ result -> result.number == 0.0 || result.number == 1.0 }),
        )
    }

    @Test
    fun `opaque literal return keeps its declared string value`() {
        discoverProperties<TsTestValue.TsString>(
            method = method("literal"),
            { result -> result.value == "known" },
            invariants = arrayOf({ result -> result.value == "known" }),
        )
    }

    private fun method(name: String): EtsMethod = getMethod(methodName = name, className = "FreshStringMock")
}
