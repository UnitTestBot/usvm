package org.usvm.machine.call

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.TsTestValue
import org.usvm.api.initializeArray
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.state.TsState
import org.usvm.sizeSort
import org.usvm.util.TsTestResolver
import org.usvm.util.getResourcePath
import org.usvm.util.markDenseInputArray
import org.usvm.util.mkRegisterStackLValue
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

class TsGapModelsTest {
    private val sourceFile = loadEtsFileAutoConvert(
        getResourcePath("/models/GapModels.ts"),
        provider = EtsIrProvider.TS_FRONTEND,
    )
    private val scene = EtsScene(listOf(sourceFile))

    @Test
    fun `split reverse join compose on UTF16 strings`() {
        val result = analyze(methodName = "reverseString")

        assertEquals("cba", assertIs<TsTestValue.TsString>(result.values.single()).value)
        assertTrue(setOf("ts.string.split", "ts.array.reverse", "ts.array.join").all(result.modelIds::contains))
        assertTrue(result.events.all { it.outcome == TsUnknownCallOutcome.MODEL_APPLIED })
    }

    @Test
    fun `split separator limit and empty field semantics`() {
        val result = analyze(methodName = "splitEdges")

        assertTrue(assertIs<TsTestValue.TsBoolean>(result.values.single()).value)
        assertTrue("ts.string.split" in result.modelIds)
    }

    @Test
    fun `reduce invokes callback with captured variables and array aliases`() {
        val result = analyze(methodName = "reduceMutation", denseInputs = listOf(listOf(1.0, 2.0, 3.0)))

        assertEquals(75.0, result.singleNumber())
        assertTrue("ts.array.reduce" in result.modelIds)
        assertTrue(result.events.all { it.outcome == TsUnknownCallOutcome.MODEL_APPLIED })
    }

    @Test
    fun `reduce handles omitted initial value`() {
        val result = analyze(methodName = "reduceNoInitial", denseInputs = listOf(listOf(1.0, 2.0, 3.0)))

        assertEquals(6.0, result.singleNumber())
        assertTrue("ts.array.reduce" in result.modelIds)
    }

    @Test
    fun `reduce handles explicit undefined initial value`() {
        val result = analyze(methodName = "reduceUndefined", denseInputs = listOf(listOf(1.0)))

        assertEquals(8.0, result.singleNumber())
        assertTrue("ts.array.reduce" in result.modelIds)
    }

    @Test
    fun `reduce structural mutation remains residual`() {
        val result = analyze(methodName = "reduceShrink", denseInputs = listOf(listOf(1.0, 2.0, 3.0)))

        assertTrue(result.values.isEmpty())
        assertTrue(result.events.any { it.outcome == TsUnknownCallOutcome.PATH_STOPPED })
    }

    @Test
    fun `parseFloat accepts longest decimal prefix and exact rounding`() {
        val result = analyze(methodName = "parseFloatEdges")

        assertTrue(assertIs<TsTestValue.TsBoolean>(result.values.single()).value)
        assertTrue("ts.number.parseFloat" in result.modelIds)
        assertTrue(result.events.all { it.outcome == TsUnknownCallOutcome.MODEL_APPLIED })
    }

    @Test
    fun `parseFloat large decimal remains residual`() {
        val result = analyze(methodName = "parseFloatUnsupported")

        assertTrue(result.values.isEmpty())
        assertTrue("ts.number.parseFloat" in result.modelIds)
        assertTrue(result.events.any { it.outcome == TsUnknownCallOutcome.PATH_STOPPED })
    }

    @Test
    fun `source algorithms agree with native JavaScript`() {
        val script = getResourcePath("/models/GapModelsDifferential.mjs")
        val sourceDirectory = getResourcePath("/org/usvm/machine/call/models/StringModels.ts").parent
        val process = ProcessBuilder(
            "node",
            "--experimental-strip-types",
            script.toString(),
            sourceDirectory.toString(),
        )
            .redirectErrorStream(true)
            .start()

        val completed = process.waitFor(30, TimeUnit.SECONDS)
        if (!completed) process.destroyForcibly()
        assertTrue(completed, "Node differential cases timed out")
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(0, process.exitValue(), output)
    }

    @Test
    fun `join preserves nullish elements separators and booleans`() {
        val strings = analyze(methodName = "joinDefault", denseInputs = listOf(listOf("a", null, "b")))
        val booleans = analyze(methodName = "joinBooleans", denseInputs = listOf(listOf(true, false)))

        assertEquals("a,,b", assertIs<TsTestValue.TsString>(strings.values.single()).value)
        assertEquals("true-false", assertIs<TsTestValue.TsString>(booleans.values.single()).value)
        assertTrue(listOf(strings, booleans).all { "ts.array.join" in it.modelIds })
    }

    @Test
    fun `callable coercions go residual without querying absent heap types`() {
        for (name in listOf("parseFloatCallable", "joinCallable", "splitCallable")) {
            val result = analyze(methodName = name)

            assertTrue(result.values.isEmpty(), name)
            assertTrue(result.events.any { it.outcome == TsUnknownCallOutcome.PATH_STOPPED }, name)
        }
    }

    @Test
    fun `empty reduce without initial throws TypeError and callback exceptions propagate`() {
        val empty = analyze(methodName = "reduceNoInitial", denseInputs = listOf(emptyList()))
        val throwing = analyze(methodName = "reduceThrows", denseInputs = listOf(listOf(1.0)))

        assertIs<TsTestValue.TsException>(empty.values.single())
        assertIs<TsTestValue.TsException>(throwing.values.single())
        assertTrue("ts.typeError.constructor" in empty.modelIds)
        assertTrue(listOf(empty, throwing).all { "ts.array.reduce" in it.modelIds })
    }

    @Test
    fun `empty reduce with initial does not invoke callback`() {
        val result = analyze(methodName = "reduceInitialEmpty", denseInputs = listOf(emptyList()))

        assertEquals(12.0, result.singleNumber())
    }

    @Test
    fun `callback deletion cannot escape as a stale exception payload`() {
        val result = analyze(methodName = "reduceDeleteThrows", denseInputs = listOf(listOf(1.0)))

        assertTrue(result.values.isEmpty())
    }

    @Test
    fun `array allocations do not invalidate another dense receiver`() {
        val result = analyze(methodName = "twoSplits")

        assertEquals("abcd", assertIs<TsTestValue.TsString>(result.values.single()).value)
    }

    @Test
    fun `callable alternatives after symbolic writes remain residual`() {
        val result = analyze(methodName = "joinCallableBranch")

        assertTrue(result.values.all { assertIs<TsTestValue.TsString>(it).value == "" })
        assertTrue(result.events.any { it.outcome == TsUnknownCallOutcome.PATH_STOPPED })
    }

    private fun analyze(
        methodName: String,
        denseInputs: List<List<Any?>> = emptyList(),
    ): AnalysisResult {
        val method = method(methodName)
        val observer = RecordingUnknownCallObserver()

        return TsMachine(
            scene = scene,
            options = machineOptions,
            tsOptions = TsOptions(),
            observer = observer,
            initialStateConfigurator = { state -> initializeDenseInputs(state, denseInputs) },
        ).use { machine ->
            val states = machine.analyze(listOf(method))

            AnalysisResult(
                values = states.map { state -> TsTestResolver().resolve(method, state).returnValue },
                events = observer.events.toList(),
            )
        }
    }

    private fun initializeDenseInputs(
        state: TsState,
        inputs: List<List<Any?>>,
    ) = with(state.ctx) {
        val arrays = inputs.mapIndexed { index, values ->
            val elementType = when (values.firstOrNull { it != null }) {
                is String -> EtsStringType
                is Boolean -> EtsBooleanType
                else -> EtsNumberType
            }
            val arrayType = EtsArrayType(elementType, dimensions = 1)
            val descriptor = arrayDescriptorOf(arrayType)
            val array = state.memory.allocConcrete(arrayType)
            state.memory.initializeArray(
                arrayHeapRef = array,
                type = descriptor,
                sort = typeToSort(elementType),
                sizeSort = sizeSort,
                contents = values.asSequence().map { value ->
                    when (value) {
                        is String -> state.mkInitializedStringConstant(value)
                        is Boolean -> mkBool(value)
                        is Number -> mkFp64(value.toDouble())
                        else -> nullRef
                    }.asExpr(typeToSort(elementType))
                },
            )
            state.memory.write(
                mkRegisterStackLValue(addressSort, index + 1),
                array.asExpr(addressSort),
                guard = trueExpr,
            )
            state.saveSortForLocal(index + 1, addressSort)
            array to arrayType
        }
        arrays.forEach { (array, arrayType) ->
            state.markDenseInputArray(array = array, type = arrayType)
        }
    }

    private fun method(name: String): EtsMethod = scene.projectClasses
        .single { it.name == "GapModels" }
        .methods
        .single { it.name == name }

    private class RecordingUnknownCallObserver : TsInterpreterObserver {
        val events = mutableListOf<TsUnknownCallEvent>()

        override fun onUnknownCall(event: TsUnknownCallEvent) {
            events += event
        }
    }

    private data class AnalysisResult(
        val values: List<TsTestValue>,
        val events: List<TsUnknownCallEvent>,
    ) {
        fun singleNumber(): Double = assertIs<TsTestValue.TsNumber>(values.single()).number

        val modelIds: List<String>
            get() = events.mapNotNull { event ->
                (event.decision as? TsUnknownCallDecision.ModelApplied)?.modelId
            }
    }

    private companion object {
        val machineOptions = UMachineOptions(
            pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            exceptionsPropagation = true,
            throwExceptionOnStepFailure = true,
            timeout = Duration.INFINITE,
            stepsFromLastCovered = 20_000L,
            solverType = SolverType.YICES,
            solverTimeout = Duration.INFINITE,
            typeOperationsTimeout = Duration.INFINITE,
        )
    }
}
