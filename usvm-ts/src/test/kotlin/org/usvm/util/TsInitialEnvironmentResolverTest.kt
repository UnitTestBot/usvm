package org.usvm.util

import org.jacodb.ets.model.EtsMethod
import org.junit.jupiter.api.Test
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UExpr
import org.usvm.UMachineOptions
import org.usvm.api.TsParametersState
import org.usvm.api.TsTestValue
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.call.TsUnknownCallModelSelection
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.machine.state.trackInitialEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration

class TsInitialEnvironmentResolverTest : TsMethodTestRunner() {
    override val scene = loadScene("/samples/lang/InitialEnvironment.ts")

    @Test
    fun `static data preserves initialized primitive object and dense array before mutation`() {
        val (method, state) = analyze(className = "InitialEnvironment", methodName = "mutate")

        val test = TsTestResolver().resolve(method, state, resolveEnvironment = true)
        val before = test.before.fields("InitialEnvironment")
        val after = test.after.fields("InitialEnvironment")

        assertEquals(11.0, before.getValue("counter").number())
        assertEquals(31.0, before.getValue("box").property("value").number())
        assertEquals(13.0, before.getValue("items").element(index = 0).number())
        assertEquals(91.0, after.getValue("counter").number())
        assertEquals(92.0, after.getValue("box").property("value").number())
        assertEquals(93.0, after.getValue("items").element(index = 0).number())
        assertEquals(55.0, test.returnValue.number())
    }

    @Test
    fun `module bindings preserve values before reassignment and object mutation`() {
        val (method, state) = analyze(className = "InitialEnvironment", methodName = "moduleData")

        val test = TsTestResolver().resolve(method, state, resolveEnvironment = true)
        val beforeValues = test.before.globals.values.flatten()
        val before = beforeValues.associate { it.field.name to it.value }
        val afterValues = test.after.globals.values.flatten()
        val after = afterValues.associate { it.field.name to it.value }

        assertEquals(17.0, before.getValue("environmentCounter").number())
        assertEquals(23.0, before.getValue("environmentBox").property("value").number())
        assertEquals(94.0, after.getValue("environmentCounter").number())
        assertEquals(95.0, after.getValue("environmentBox").property("value").number())
        assertEquals(40.0, test.returnValue.number())
    }

    @Test
    fun `static object and array aliases share the same resolved identity`() {
        val (method, state) = analyze(className = "InitialEnvironment", methodName = "aliases")

        val inputs = TsTestResolver().resolveInputs(method = method, state = state)
        val fields = inputs.fields("InitialEnvironment")

        assertSame(fields.getValue("box"), fields.getValue("aliasBox"))
        assertSame(fields.getValue("items"), fields.getValue("aliasItems"))
    }

    @Test
    fun `tracking metadata failures do not suppress a normal returned state`() {
        val captureFailures = listOf(
            NotImplementedError("fixture missing metadata"),
            IllegalArgumentException("fixture"),
        )
        for (captureFailure in captureFailures) {
            val (method, state) = analyze(className = "InitialEnvironment", methodName = "lazy") { state ->
                state.trackInitialEnvironment { throw captureFailure }
            }

            assertIs<TsMethodResult.Success>(state.methodResult)
            val failure = assertFailsWith<TsUnsupportedEnvironmentWitnessException> {
                TsTestResolver().resolveInputs(method = method, state = state)
            }

            assertTrue(failure.message.orEmpty().contains("Initial environment capture"))
        }
    }

    @Test
    fun `first lazy initialization supplies concrete data rather than model defaults`() {
        val (method, state) = analyze(className = "InitialEnvironment", methodName = "lazy")

        val inputs = TsTestResolver().resolveInputs(method = method, state = state)

        assertEquals(11.0, inputs.fields("InitialEnvironment").getValue("counter").number())
    }

    @Test
    fun `later natural initializer updates earlier owner baseline before first user read`() {
        val (method, state) = analyze(className = "LaterEnvironment", methodName = "run")

        val test = TsTestResolver().resolve(method, state, resolveEnvironment = true)

        val earlier = test.before.fields("EarlierEnvironment")
        assertEquals(41.0, earlier.getValue("box").property("value").number())
        assertEquals(42.0, test.returnValue.number())
    }

    @Test
    fun `interleaved lazy initializer keeps normal execution but rejects inconsistent environment extraction`() {
        val (method, state) = analyze(className = "InterleavedEnvironment", methodName = "run")

        assertIs<TsMethodResult.Success>(state.methodResult)
        val legacy = TsTestResolver().resolve(method, state)
        assertEquals(51.0, legacy.returnValue.number())
        val failure = assertFailsWith<TsUnsupportedEnvironmentWitnessException> {
            TsTestResolver().resolveInputs(method = method, state = state)
        }

        assertTrue(failure.message.orEmpty().contains("initializer ran after"))
        val partial = TsTestResolver().resolveInputs(method = method, state = state, resolveEnvironment = false)
        assertTrue(partial.globals.isEmpty())
        assertIs<TsTestValue.TsClass>(partial.thisInstance)
    }

    @Test
    fun `sparse initialized environment is explicitly unsupported instead of fabricated dense values`() {
        val (method, state) = analyze(className = "SparseEnvironment", methodName = "run")

        assertIs<TsMethodResult.Success>(state.methodResult)
        val failure = assertFailsWith<TsUnsupportedEnvironmentWitnessException> {
            TsTestResolver().resolveInputs(method = method, state = state)
        }

        assertTrue(failure.message.orEmpty().contains("Sparse initial environment arrays"))
    }

    @Test
    fun `stopped initializer rejects full environment while preserving original roots`() {
        val method = getMethod(methodName = "run", className = "PendingInitialEnvironment")
        var receiver: UExpr<*>? = null
        val failures = mutableListOf<TsUnsupportedEnvironmentWitnessException>()
        val partials = mutableListOf<TsParametersState>()
        val observer = object : TsInterpreterObserver {
            override fun onPathStopped(state: TsState, reason: String) {
                failures += assertFailsWith<TsUnsupportedEnvironmentWitnessException> {
                    TsTestResolver().resolveInputs(method = method, state = state, receiverValue = receiver)
                }
                partials += TsTestResolver().resolveInputs(
                    method = method,
                    state = state,
                    receiverValue = receiver,
                    resolveEnvironment = false,
                )
            }
        }

        TsMachine(
            scene = scene,
            options = analysisOptions,
            tsOptions = TsOptions(unknownCallModelSelection = TsUnknownCallModelSelection.Only(emptySet())),
            observer = observer,
        ).use { machine ->
            machine.analyze(methods = listOf(method), configureInitialState = { _, state ->
                receiver = state.memory.read(mkRegisterStackLValue(state.ctx.addressSort, idx = 0))
            })
        }

        assertTrue(failures.isNotEmpty())
        assertTrue(failures.all { it.message.orEmpty().contains("initializer has not completed") })
        assertTrue(partials.all { it.globals.isEmpty() && it.thisInstance is TsTestValue.TsClass })
    }

    private fun analyze(
        className: String,
        methodName: String,
        configure: (TsState) -> Unit = {},
    ): Pair<EtsMethod, TsState> {
        val method = getMethod(methodName = methodName, className = className)
        val states = TsMachine(scene = scene, options = analysisOptions, tsOptions = TsOptions()).use { machine ->
            machine.analyze(methods = listOf(method), configureInitialState = { _, state -> configure(state) })
        }

        return method to states.single()
    }

    private fun TsParametersState.fields(className: String): Map<String, TsTestValue> =
        globals.entries.single { it.key.name == className }.value.associate { it.field.name to it.value }

    private fun TsTestValue.number(): Double = assertIs<TsTestValue.TsNumber>(this).number

    private fun TsTestValue.property(name: String): TsTestValue =
        assertIs<TsTestValue.TsClass>(this).properties.getValue(name)

    private fun TsTestValue.element(index: Int): TsTestValue = assertIs<TsTestValue.TsArray<*>>(this).values[index]

    private val analysisOptions = UMachineOptions(
        pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
        stateCollectionStrategy = StateCollectionStrategy.ALL,
        solverType = SolverType.YICES,
        solverTimeout = Duration.INFINITE,
        typeOperationsTimeout = Duration.INFINITE,
        stopOnCoverage = 0,
    )
}
