package org.usvm.machine

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.usvm.PathSelectionStrategy
import org.usvm.PathSelectorFairnessStrategy
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.TsTest
import org.usvm.api.TsTestValue
import org.usvm.machine.state.TsState
import org.usvm.statistics.UMachineObserver
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.TsTestResolver
import org.usvm.util.assertNodeReplay
import org.usvm.util.getResourcePath
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Queue, budget and ownership contracts; generated witnesses are also checked in the native runtime. */
class TsAnalysisSessionTest : TsMethodTestRunner() {
    @TempDir
    lateinit var directory: Path

    private val source = getResourcePath("/samples/lang/Exceptions.ts")
    override val scene = EtsScene(listOf(loadEtsFileAutoConvert(source, provider = EtsIrProvider.TS_FRONTEND)))
    private val method by lazy { getMethod(methodName = "conditionalCatch", className = "Exceptions") }
    private val analysisOptions = UMachineOptions(
        solverType = SolverType.YICES,
        pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
        stateCollectionStrategy = StateCollectionStrategy.ALL,
        stopOnCoverage = 0,
        timeout = Duration.INFINITE,
        throwExceptionOnStepFailure = true,
    )

    @Test
    fun `one step phases reuse the initial state and prefix queries and preserve native outcomes`() {
        val direct = execute(phased = false)

        val phased = execute(phased = true)

        assertEquals(expected = 1, actual = phased.initializations)
        assertEquals(expected = direct.steps, actual = phased.steps)
        assertEquals(expected = direct.queries, actual = phased.queries)
        assertTrue(phased.phases > 1)
        assertEquals(expected = setOf(1.0, 2.0), actual = phased.tests.map(::resultNumber).toSet())
        assertEquals(
            expected = direct.tests.map(::resultNumber).toSet(),
            actual = phased.tests.map(::resultNumber).toSet(),
        )

        val script = buildString {
            appendLine(source.readText())
            phased.tests.forEach { test ->
                val input = assertIs<TsTestValue.TsNumber>(test.before.parameters.single()).number
                val expected = if (input == 0.0) 2.0 else 1.0
                assertEquals(expected = expected, actual = resultNumber(test))

                appendLine("if (new Exceptions().conditionalCatch($input) !== $expected) {")
                appendLine("  throw Error('session witness');")
                appendLine("}")
            }
        }
        assertNodeReplay(
            source = script,
            directory = directory,
            name = "analysis-session",
            timeoutMessage = "Native replay of retained-session witnesses timed out",
        )
    }

    @Test
    fun `analysis step limit is cumulative and cannot be reset by another advance`() {
        var steps = 0
        val observer = object : UMachineObserver<TsState> {
            override fun onState(parent: TsState, forks: Sequence<TsState>) {
                steps++
            }
        }
        val options = analysisOptions.copy(stepLimit = 2uL)

        TsMachine(
            scene = scene,
            options = options,
            tsOptions = TsOptions(),
            machineObserver = observer,
        ).use { machine ->
            machine.openAnalysis(methods = listOf(method)).use { session ->
                val first = session.advance(maxSteps = 1uL)
                assertEquals(expected = TsAnalysisStopReason.STOPPED, actual = first.stopReason)
                assertFalse(session.isFinished)

                val second = session.advance(maxSteps = 1uL)
                assertEquals(expected = TsAnalysisStopReason.STOPPED, actual = second.stopReason)
                assertTrue(session.isFinished)
                assertFalse(second.timedOut)
                assertEquals(expected = 2, actual = steps)

                assertEquals(expected = second, actual = session.advance())
                assertEquals(expected = 2, actual = steps)
            }
        }
    }

    @Test
    fun `phase snapshots do not acquire states collected by later advances`() {
        TsMachine(scene = scene, options = analysisOptions, tsOptions = TsOptions()).use { machine ->
            machine.openAnalysis(methods = listOf(method)).use { session ->
                var snapshot = session.advance(maxSteps = 1uL)
                var phases = 1
                while (snapshot.states.isEmpty() && !session.isFinished) {
                    assertTrue(phases++ < MAX_PHASES)
                    snapshot = session.advance(maxSteps = 1uL)
                }
                assertEquals(expected = 1, actual = snapshot.states.size)
                val firstState = snapshot.states.single()

                val final = session.advance()

                assertEquals(expected = TsAnalysisStopReason.EXHAUSTED, actual = final.stopReason)
                assertEquals(expected = 2, actual = final.states.size)
                assertEquals(expected = listOf(firstState), actual = snapshot.states)
            }
        }
    }

    @Test
    fun `closing a session releases the machine and closing the machine invalidates the session`() {
        val machine = TsMachine(scene = scene, options = analysisOptions, tsOptions = TsOptions())
        machine.use {
            val session = machine.openAnalysis(methods = listOf(method))
            session.advance(maxSteps = 1uL)
            assertFailsWith<IllegalStateException> { machine.openAnalysis(methods = listOf(method)) }
            assertFailsWith<IllegalStateException> { machine.analyzeWithMetadata(methods = listOf(method)) }

            session.close()
            session.close()

            assertTrue(session.isFinished)
            assertFailsWith<IllegalStateException> { session.advance() }
            assertEquals(
                expected = TsAnalysisStopReason.EXHAUSTED,
                actual = machine.analyzeWithMetadata(methods = listOf(method)).stopReason,
            )

            val next = machine.openAnalysis(methods = listOf(method))
            machine.close()

            assertTrue(next.isFinished)
            assertFailsWith<IllegalStateException> { next.advance() }
            assertFailsWith<IllegalStateException> { machine.openAnalysis(methods = listOf(method)) }
        }
    }

    @Test
    fun `timeout stops the analysis rather than yielding a resumable phase`() {
        var steps = 0
        val observer = object : UMachineObserver<TsState> {
            override fun onState(parent: TsState, forks: Sequence<TsState>) {
                steps++
            }
        }
        val options = analysisOptions.copy(timeout = Duration.ZERO)

        TsMachine(
            scene = scene,
            options = options,
            tsOptions = TsOptions(),
            machineObserver = observer,
        ).use { machine ->
            machine.openAnalysis(methods = listOf(method)).use { session ->
                val result = session.advance(maxSteps = 1uL)

                assertTrue(result.timedOut)
                assertTrue(session.isFinished)
                assertEquals(expected = 0, actual = steps)
                assertEquals(expected = result, actual = session.advance())
            }
        }
    }

    @Test
    fun `empty analysis is finished without invoking machine lifecycle observers`() {
        var starts = 0
        val observer = object : UMachineObserver<TsState> {
            override fun onMachineStarted() {
                starts++
            }
        }

        TsMachine(
            scene = scene,
            options = analysisOptions,
            tsOptions = TsOptions(),
            machineObserver = observer,
        ).use { machine ->
            machine.openAnalysis(methods = emptyList()).use { session ->
                assertTrue(session.isFinished)

                val result = session.advance()

                assertEquals(expected = TsAnalysisStopReason.EXHAUSTED, actual = result.stopReason)
                assertTrue(result.states.isEmpty())
                assertEquals(expected = 0, actual = starts)
            }
        }
    }

    @Test
    fun `bounded phases reject multiple runnable entries without changing uninterrupted scheduling`() {
        val other = getMethod(methodName = "caughtValue", className = "Exceptions")
        val methods = listOf(method, other)

        for (fairness in PathSelectorFairnessStrategy.entries) {
            val options = analysisOptions.copy(pathSelectorFairnessStrategy = fairness, timeout = 10.seconds)
            TsMachine(scene = scene, options = options, tsOptions = TsOptions()).use { machine ->
                val session = machine.openAnalysis(methods = methods)

                val failure = assertFailsWith<IllegalArgumentException> { session.advance(maxSteps = 1uL) }

                assertTrue(failure.message.orEmpty().contains("one runnable entry point"))
                assertTrue(session.isFinished)

                val result = machine.analyzeWithMetadata(methods = methods)

                assertEquals(expected = TsAnalysisStopReason.EXHAUSTED, actual = result.stopReason)
                assertEquals(expected = methods.toSet(), actual = result.states.map { it.entrypoint }.toSet())
            }
        }
    }

    @Test
    fun `initial state callbacks cannot open a nested analysis and failed preparation releases the owner`() {
        TsMachine(scene = scene, options = analysisOptions, tsOptions = TsOptions()).use { machine ->
            val failure = assertFailsWith<IllegalStateException> {
                machine.openAnalysis(methods = listOf(method), configureInitialState = { _, _ ->
                    machine.openAnalysis(methods = emptyList())
                })
            }

            assertTrue(failure.message.orEmpty().contains("current analysis session"))
            val result = machine.analyzeWithMetadata(methods = listOf(method))

            assertEquals(expected = TsAnalysisStopReason.EXHAUSTED, actual = result.stopReason)
        }
    }

    @Test
    fun `an exceptional advance invalidates its session and releases the machine`() {
        var failNextStep = true
        val observer = object : UMachineObserver<TsState> {
            override fun onStatePeeked(state: TsState) {
                if (failNextStep) {
                    failNextStep = false
                    error("phase observer failed")
                }
            }
        }

        TsMachine(
            scene = scene,
            options = analysisOptions,
            tsOptions = TsOptions(),
            machineObserver = observer,
        ).use { machine ->
            val session = machine.openAnalysis(methods = listOf(method))

            val failure = assertFailsWith<IllegalStateException> { session.advance(maxSteps = 1uL) }

            assertEquals(expected = "phase observer failed", actual = failure.message)
            assertTrue(session.isFinished)
            assertFailsWith<IllegalStateException> { session.advance() }
            assertEquals(
                expected = TsAnalysisStopReason.EXHAUSTED,
                actual = machine.analyzeWithMetadata(methods = listOf(method)).stopReason,
            )
        }
    }

    private fun execute(phased: Boolean): Execution {
        var initializations = 0
        var steps = 0
        var queries = 0
        var phases = 0
        val machineObserver = object : UMachineObserver<TsState> {
            override fun onState(parent: TsState, forks: Sequence<TsState>) {
                steps++
            }
        }
        val interpreterObserver = object : TsInterpreterObserver {
            override fun onSolverQuery(event: TsSolverQueryEvent) {
                queries++
            }
        }

        val outcome = TsMachine(
            scene = scene,
            options = analysisOptions,
            tsOptions = TsOptions(),
            machineObserver = machineObserver,
            observer = interpreterObserver,
        ).use { machine ->
            machine.openAnalysis(methods = listOf(method), configureInitialState = { _, _ -> initializations++ })
                .use { session ->
                    var result: TsMachineAnalysisResult
                    do {
                        assertTrue(phases++ < MAX_PHASES)
                        result = session.advance(maxSteps = if (phased) 1uL else null)
                    } while (!session.isFinished)

                    val stepsAtFinish = steps
                    val queriesAtFinish = queries
                    session.advance()
                    assertEquals(expected = stepsAtFinish, actual = steps)
                    assertEquals(expected = queriesAtFinish, actual = queries)
                    result
                }
        }

        assertEquals(expected = TsAnalysisStopReason.EXHAUSTED, actual = outcome.stopReason)
        assertFalse(outcome.engineFailed)
        assertFalse(outcome.unsupportedCall)
        assertTrue(outcome.unsupportedPaths.isEmpty())

        return Execution(
            initializations = initializations,
            steps = steps,
            queries = queries,
            phases = phases,
            tests = outcome.states.map { state -> TsTestResolver().resolve(method, state) },
        )
    }

    private fun resultNumber(test: TsTest): Double = assertIs<TsTestValue.TsNumber>(test.returnValue).number

    private data class Execution(
        val initializations: Int,
        val steps: Int,
        val queries: Int,
        val phases: Int,
        val tests: List<TsTest>,
    )

    companion object {
        private const val MAX_PHASES = 200
    }
}
