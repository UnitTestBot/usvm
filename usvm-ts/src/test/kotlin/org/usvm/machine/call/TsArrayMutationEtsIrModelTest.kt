package org.usvm.machine.call

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsScene
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

class TsArrayMutationEtsIrModelTest {
    private val sourceFile = loadEtsFileAutoConvert(
        getResourcePath("/models/ArrayMutationEtsIr.ts"),
        provider = EtsIrProvider.TS_FRONTEND,
    )
    private val scene = EtsScene(listOf(sourceFile))

    @Test
    fun `push supports zero through three arguments in source`() {
        val result = analyze(methodName = "pushSupportedArities")

        assertEquals(1447.0, result.singleNumber())
        assertEquals(setOf("ts.array.push", "ts.array.primitive.grow"), result.modelIds.toSet())
        assertEquals(3, result.modelIds.count { it == "ts.array.push" })
    }

    @Test
    fun `push outside bounded arity uses residual fallback`() {
        val result = analyze(methodName = "pushTooManyArguments")

        assertTrue(result.values.isEmpty())
        assertEquals(TsUnknownCallOutcome.PATH_STOPPED, result.events.last().outcome)
        assertTrue(result.modelIds.isEmpty())
    }

    @Test
    fun `fill normalizes negative fractions infinities and NaN`() {
        val negative = analyze(
            methodName = "fillNegativeFractionAndInfinity",
            denseInputs = listOf(listOf(1.0, 2.0, 3.0, 4.0)),
        )
        val nan = analyze(
            methodName = "fillNaNAndFraction",
            denseInputs = listOf(listOf(1.0, 2.0, 3.0, 4.0)),
        )

        assertEquals(1299.0, negative.singleNumber())
        assertEquals(7234.0, nan.singleNumber())
        assertTrue(listOf(negative, nan).all { "ts.array.fill" in it.modelIds })
    }

    @Test
    fun `reverse and unshift mutate bounded dense arrays`() {
        val reversed = analyze(
            methodName = "reverseDense",
            denseInputs = listOf(listOf(1.0, 2.0, 3.0, 4.0)),
        )
        val unshifted = analyze(
            methodName = "unshiftDense",
            denseInputs = listOf(listOf(1.0, 2.0)),
        )

        assertEquals(4321.0, reversed.singleNumber())
        assertEquals(47_812.0, unshifted.singleNumber())
        assertTrue("ts.array.reverse" in reversed.modelIds)
        assertTrue("ts.array.unshift" in unshifted.modelIds)
    }

    @Test
    fun `slice and concat copy bounded dense arrays`() {
        val sliced = analyze(
            methodName = "sliceDense",
            denseInputs = listOf(listOf(1.0, 2.0, 3.0, 4.0)),
        )
        val concatenated = analyze(
            methodName = "concatDense",
            denseInputs = listOf(listOf(1.0, 2.0), listOf(3.0, 4.0)),
        )

        assertEquals(32_344.0, sliced.singleNumber())
        assertEquals(41_234.0, concatenated.singleNumber())
        assertTrue("ts.array.slice" in sliced.modelIds)
        assertTrue("ts.array.concat" in concatenated.modelIds)
    }

    @Test
    fun `sparse reverse remains residual`() {
        val result = analyze(methodName = "sparseReverseUsesResidual")

        assertTrue(result.values.isEmpty())
        assertEquals(TsUnknownCallOutcome.PATH_STOPPED, result.events.last().outcome)
        assertTrue(result.modelIds.isEmpty())
    }

    private fun analyze(
        methodName: String,
        denseInputs: List<List<Double>> = emptyList(),
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
        inputs: List<List<Double>>,
    ) = with(state.ctx) {
        val arrayType = EtsArrayType(EtsNumberType, dimensions = 1)
        val descriptor = arrayDescriptorOf(arrayType)
        val arrays = inputs.mapIndexed { index, values ->
            val array = state.memory.allocConcrete(descriptor)
            state.memory.initializeArray(
                arrayHeapRef = array,
                type = descriptor,
                sort = fp64Sort,
                sizeSort = sizeSort,
                contents = values.asSequence().map(::mkFp64),
            )
            state.memory.write(
                mkRegisterStackLValue(addressSort, index + 1),
                array.asExpr(addressSort),
                guard = trueExpr,
            )
            state.saveSortForLocal(index + 1, addressSort)
            array
        }
        arrays.forEach { array ->
            state.markDenseInputArray(array = array, type = arrayType)
        }
    }

    private fun method(name: String): EtsMethod = scene.projectClasses
        .single { it.name == "ArrayMutationEtsIr" }
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
