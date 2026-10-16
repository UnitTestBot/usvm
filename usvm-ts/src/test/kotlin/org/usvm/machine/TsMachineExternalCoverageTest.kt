package org.usvm.machine

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.CoverageZone
import org.usvm.PathSelectionStrategy
import org.usvm.StateCollectionStrategy
import org.usvm.machine.state.TsState
import org.usvm.statistics.UMachineObserver
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.getResourcePath
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration

/** External coverage affects scheduling only, independently of native collection and stopping. */
class TsMachineExternalCoverageTest : TsMethodTestRunner() {
    override val scene = EtsScene(
        projectFiles = listOf(
            loadEtsFileAutoConvert(
                getResourcePath("/samples/coverage/ClassCoverage.ts"),
                provider = EtsIrProvider.TS_FRONTEND,
            ),
        ),
    )

    @Test
    fun `TypeScript defaults to NURS and respects an explicit strategy`() {
        TsMachine(scene = scene, tsOptions = TsOptions()).use { machine ->
            assertEquals(
                expected = listOf(PathSelectionStrategy.CLOSEST_TO_UNCOVERED_RANDOM),
                actual = machine.options.pathSelectionStrategies,
            )
        }
        val explicitOptions = options.copy(pathSelectionStrategies = listOf(PathSelectionStrategy.BFS))

        TsMachine(scene = scene, options = explicitOptions, tsOptions = TsOptions()).use { machine ->
            assertEquals(expected = listOf(PathSelectionStrategy.BFS), actual = machine.options.pathSelectionStrategies)
        }
    }

    @Test
    fun `external saturation before analysis neither stops execution nor suppresses native witnesses`() {
        val method = getMethod(methodName = "entry", className = "HelperCoverage")
        val statements = scene.projectClasses.flatMap { owner -> owner.methods.flatMap { it.cfg.stmts } }
        val analysisOptions = options.copy(
            coverageZone = CoverageZone.CLASS,
            stateCollectionStrategy = StateCollectionStrategy.COVERED_NEW,
            stopOnCoverage = 100,
            timeout = Duration.INFINITE,
        )

        TsMachine(scene = scene, options = analysisOptions, tsOptions = TsOptions()).use { machine ->
            assertTrue(machine.markCovered(statements))
            assertFalse(machine.markCovered(statements))

            repeat(2) {
                val result = machine.analyzeWithMetadata(methods = listOf(method))

                assertEquals(expected = TsAnalysisStopReason.EXHAUSTED, actual = result.stopReason)
                assertEquals(expected = 2, actual = result.states.size)
                assertFalse(result.engineFailed)
            }
        }
    }

    @Test
    fun `external coverage can arrive on the analysis thread while paths are queued`() {
        val method = getMethod(methodName = "entry", className = "HelperCoverage")
        val statements = scene.projectClasses.flatMap { owner -> owner.methods.flatMap { it.cfg.stmts } }
        lateinit var machine: TsMachine
        var reported = false
        val observer = object : UMachineObserver<TsState> {
            override fun onStatePeeked(state: TsState) {
                if (!reported) {
                    reported = machine.markCovered(statements)
                }
            }
        }
        val analysisOptions = options.copy(
            coverageZone = CoverageZone.CLASS,
            stateCollectionStrategy = StateCollectionStrategy.COVERED_NEW,
            stopOnCoverage = 0,
            timeout = Duration.INFINITE,
        )
        machine = TsMachine(
            scene = scene,
            options = analysisOptions,
            tsOptions = TsOptions(),
            machineObserver = observer,
        )

        val result = machine.use { it.analyzeWithMetadata(methods = listOf(method)) }

        assertTrue(reported)
        assertEquals(expected = TsAnalysisStopReason.EXHAUSTED, actual = result.stopReason)
        assertEquals(expected = 2, actual = result.states.size)
        assertFalse(result.engineFailed)
    }
}
