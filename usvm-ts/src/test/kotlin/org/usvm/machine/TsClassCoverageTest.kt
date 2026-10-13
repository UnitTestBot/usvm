package org.usvm.machine

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.CoverageZone
import org.usvm.PathSelectionStrategy
import org.usvm.StateCollectionStrategy
import org.usvm.api.TsTestValue
import org.usvm.util.TsMethodTestRunner
import org.usvm.util.eq
import org.usvm.util.getResourcePath
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.usvm.test.util.checkers.eq as exactly

class TsClassCoverageTest : TsMethodTestRunner() {
    override val scene = EtsScene(
        projectFiles = listOf(
            loadEtsFileAutoConvert(
                getResourcePath("/samples/coverage/ClassCoverage.ts"),
                provider = EtsIrProvider.TS_FRONTEND,
            ),
        ),
    )

    @Test
    fun `class coverage retains new paths in a private helper`() {
        val method = getMethod(methodName = "entry", className = "HelperCoverage")
        val classOptions = options.copy(
            coverageZone = CoverageZone.CLASS,
            stateCollectionStrategy = StateCollectionStrategy.COVERED_NEW,
            stopOnCoverage = 0,
        )

        withOptions(options = classOptions) {
            checkMatches<TsTestValue.TsNumber, TsTestValue.TsNumber>(
                method = method,
                analysisResultNumberMatches = exactly(2),
                { value, result -> value.number > 0 && result eq 1 },
                { value, result -> !(value.number > 0) && result eq 2 },
            )
        }
    }

    @Test
    fun `method coverage still ignores new paths outside the entry method`() {
        val method = getMethod(methodName = "entry", className = "HelperCoverage")
        val methodOptions = options.copy(
            coverageZone = CoverageZone.METHOD,
            stateCollectionStrategy = StateCollectionStrategy.COVERED_NEW,
            stopOnCoverage = 0,
        )

        withOptions(options = methodOptions) {
            checkMatches<TsTestValue.TsNumber, TsTestValue.TsNumber>(
                method = method,
                analysisResultNumberMatches = exactly(1),
                { _, result -> result eq 1 || result eq 2 },
            )
        }
    }

    @Test
    fun `class coverage retains new paths in the constructor`() {
        val method = getMethod(methodName = "entry", className = "ConstructorCoverage")
        val classOptions = options.copy(
            coverageZone = CoverageZone.CLASS,
            stateCollectionStrategy = StateCollectionStrategy.COVERED_NEW,
            stopOnCoverage = 0,
        )

        withOptions(options = classOptions) {
            checkMatches<TsTestValue.TsNumber, TsTestValue.TsNumber>(
                method = method,
                analysisResultNumberMatches = exactly(2),
                { value, result -> value.number > 0 && result eq 0 },
                { value, result -> !(value.number > 0) && result eq 0 },
            )
        }
    }

    @Test
    fun `class coverage excludes helper methods belonging to another class`() {
        val method = getMethod(methodName = "entry", className = "ForeignCaller")
        val classOptions = options.copy(
            coverageZone = CoverageZone.CLASS,
            stateCollectionStrategy = StateCollectionStrategy.COVERED_NEW,
            stopOnCoverage = 0,
        )

        withOptions(options = classOptions) {
            checkMatches<TsTestValue.TsNumber, TsTestValue.TsNumber>(
                method = method,
                analysisResultNumberMatches = exactly(1),
                { _, result -> result eq 10 || result eq 20 },
            )
        }
    }

    @Test
    fun `unvisited class methods prevent the method coverage stop`() {
        val method = getMethod(methodName = "entry", className = "HelperCoverage")
        val classOptions = options.copy(
            coverageZone = CoverageZone.CLASS,
            stopOnCoverage = 100,
            timeout = Duration.INFINITE,
        )

        val result = TsMachine(
            scene = scene,
            options = classOptions,
            tsOptions = TsOptions(),
        ).use { machine ->
            machine.analyzeWithOutcome(methods = listOf(method))
        }

        assertEquals(expected = TsAnalysisStopReason.EXHAUSTED, actual = result.stopReason)
        assertEquals(expected = 2, actual = result.states.size)
    }

    @Test
    fun `class coverage combines the declaring classes of all entry points`() {
        val helperEntry = getMethod(methodName = "entry", className = "HelperCoverage")
        val constructorEntry = getMethod(methodName = "entry", className = "ConstructorCoverage")
        val classOptions = options.copy(
            coverageZone = CoverageZone.CLASS,
            stateCollectionStrategy = StateCollectionStrategy.COVERED_NEW,
            stopOnCoverage = 0,
            timeout = Duration.INFINITE,
        )

        val result = TsMachine(
            scene = scene,
            options = classOptions,
            tsOptions = TsOptions(),
        ).use { machine ->
            machine.analyzeWithOutcome(methods = listOf(helperEntry, constructorEntry))
        }

        val statesByEntryPoint = result.states.groupingBy { state -> state.entrypoint.signature }.eachCount()
        assertEquals(
            expected = mapOf(helperEntry.signature to 2, constructorEntry.signature to 2),
            actual = statesByEntryPoint,
        )
    }

    @Test
    fun `budget search can retain pending states after entry method coverage`() {
        val counts = analyzePendingEntryPoints(removeCoveredEntryPoints = false)

        assertEquals(expected = mapOf("ForeignCaller" to 2, "HelperCoverage" to 1), actual = counts)
    }

    @Test
    fun `covered entry point pruning retains its default behavior`() {
        val counts = analyzePendingEntryPoints(removeCoveredEntryPoints = true)

        assertEquals(expected = mapOf("ForeignCaller" to 1, "HelperCoverage" to 1), actual = counts)
    }

    @Test
    fun `explicit class targets collect private and constructor paths through wrappers`() {
        val helper = getMethod(methodName = "helperCase", className = "CoverageHarness")
        val constructor = getMethod(methodName = "constructorCase", className = "CoverageHarness")
        val targets = scene.projectClasses.filter { owner ->
            owner.name == "HelperCoverage" || owner.name == "ConstructorCoverage"
        }.mapTo(hashSetOf()) { owner -> owner.signature }
        val classOptions = options.copy(
            pathSelectionStrategies = listOf(PathSelectionStrategy.CLOSEST_TO_UNCOVERED_RANDOM),
            coverageZone = CoverageZone.CLASS,
            stateCollectionStrategy = StateCollectionStrategy.COVERED_NEW,
            stopOnCoverage = 0,
            timeout = 10.seconds,
            maxEntryPointTimeQuantum = 1.milliseconds,
            removeCoveredEntryPoints = false,
        )

        val result = TsMachine(
            scene = scene,
            options = classOptions,
            tsOptions = TsOptions(classCoverageTargets = targets),
        ).use { machine ->
            machine.analyzeWithOutcome(methods = listOf(helper, constructor))
        }

        val statesByEntryPoint = result.states.groupingBy { state -> state.entrypoint.name }.eachCount()
        assertEquals(expected = mapOf("helperCase" to 2, "constructorCase" to 2), actual = statesByEntryPoint)
    }

    @Test
    fun `an unknown class target is rejected instead of falling back to the wrapper`() {
        val method = getMethod(methodName = "entry", className = "HelperCoverage")
        val missingTarget = method.signature.enclosingClass.copy(name = "MissingCoverageClass")

        assertFailsWith<IllegalArgumentException> {
            TsMachine(
                scene = scene,
                options = options.copy(coverageZone = CoverageZone.CLASS),
                tsOptions = TsOptions(classCoverageTargets = setOf(missingTarget)),
            ).use { machine ->
                machine.analyzeWithOutcome(methods = listOf(method))
            }
        }
    }

    @Test
    fun `an explicit empty class target set is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            TsOptions(classCoverageTargets = emptySet())
        }
    }

    @Test
    fun `explicit class targets cannot silently use method coverage`() {
        val method = getMethod(methodName = "entry", className = "HelperCoverage")

        assertFailsWith<IllegalArgumentException> {
            TsMachine(
                scene = scene,
                options = options.copy(coverageZone = CoverageZone.METHOD),
                tsOptions = TsOptions(classCoverageTargets = setOf(method.signature.enclosingClass)),
            ).use { machine ->
                machine.analyzeWithOutcome(methods = listOf(method))
            }
        }
    }

    private fun analyzePendingEntryPoints(removeCoveredEntryPoints: Boolean): Map<String, Int> {
        val caller = getMethod(methodName = "entry", className = "ForeignCaller")
        val sibling = getMethod(methodName = "unvisited", className = "HelperCoverage")
        val schedulingOptions = options.copy(
            pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
            coverageZone = CoverageZone.METHOD,
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            stopOnCoverage = 0,
            timeout = 10.seconds,
            maxEntryPointTimeQuantum = 1.milliseconds,
            removeCoveredEntryPoints = removeCoveredEntryPoints,
        )

        val result = TsMachine(
            scene = scene,
            options = schedulingOptions,
            tsOptions = TsOptions(),
        ).use { machine ->
            machine.analyzeWithOutcome(methods = listOf(caller, sibling))
        }

        assertEquals(expected = TsAnalysisStopReason.EXHAUSTED, actual = result.stopReason)
        return result.states.groupingBy { state -> state.entrypoint.signature.enclosingClass.name }.eachCount()
    }
}
