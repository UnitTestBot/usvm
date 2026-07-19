package org.usvm.machine.targets

import io.mockk.every
import io.mockk.mockk
import org.jacodb.ets.model.EtsIfStmt
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.usvm.PathNode
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.StateCollectionStrategy
import org.usvm.UCallStack
import org.usvm.UMachineOptions
import org.usvm.api.targets.ReachabilityObserver
import org.usvm.api.targets.TsReachabilityTarget
import org.usvm.api.targets.TsTarget
import org.usvm.machine.TsGraph
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.state.TsState
import org.usvm.statistics.CompositeUMachineObserver
import org.usvm.statistics.StepsStatistics
import org.usvm.statistics.distances.CfgStatisticsImpl
import org.usvm.statistics.distances.PlainCallGraphStatistics
import org.usvm.targets.UTargetsSet
import org.usvm.util.getResourcePath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration

class TsTargetReachabilityPrunerTest {
    private val scene = run {
        val resource = getResourcePath("/reachability/TargetReachabilityPruning.ts")
        EtsScene(listOf(loadEtsFileAutoConvert(resource)))
    }
    private val graph = TsGraph(scene)
    private val cfgStatistics = CfgStatisticsImpl(graph)

    @Test
    fun `current if keeps target successor and rejects unreachable fork`() {
        val method = method("criticalFork")
        val ifStatement = method.cfg.stmts.filterIsInstance<EtsIfStmt>().single()
        val (targetSuccessor, unreachableSuccessor) = graph.successors(ifStatement).take(2).toList()
        val currentTarget = TsReachabilityTarget.IntermediatePoint(ifStatement)
        currentTarget.addChild(TsReachabilityTarget.FinalPoint(targetSuccessor))
        val state = stateAt(method, ifStatement, listOf(currentTarget))
        val pruner = pruner()

        assertTrue(
            pruner.shouldForkTo(state, targetSuccessor),
            "The successor containing the active child target was lost",
        )
        assertFalse(pruner.shouldForkTo(state, unreachableSuccessor), "A CFG-proven unreachable fork was retained")

        val statistics = pruner.statistics
        assertEquals(1, statistics.unreachablePruned)
        assertEquals(1, statistics.rejectionCount(TS_TARGET_UNREACHABLE_PRUNED_REASON))
        assertEquals(0, statistics.rejectionCount("some_future_reason"))
        assertEquals(2, statistics.forkCandidates)
        assertEquals(2, statistics.activeTargetPairs)
        assertEquals(2, statistics.effectiveTargetPairs)
    }

    @Test
    fun `multiple roots keep every successor that reaches at least one active target`() {
        val method = method("criticalFork")
        val ifStatement = method.cfg.stmts.filterIsInstance<EtsIfStmt>().single()
        val successors = graph.successors(ifStatement).take(2).toList()
        val roots = successors.map(::TsTarget)
        val state = stateAt(method, ifStatement, roots)
        val pruner = pruner()

        successors.forEach { successor ->
            assertTrue(pruner.shouldForkTo(state, successor), "A successor for one of multiple roots was pruned")
        }

        assertEquals(0, pruner.statistics.unreachablePruned)
        assertEquals(2, pruner.statistics.maxActiveTargets)
        assertTrue(pruner.statistics.activeTargetPairs >= pruner.statistics.distanceEvaluations)
    }

    @Test
    fun `loop body remains reachable through back edge`() {
        val method = method("loopBackEdge")
        val loopCondition = method.cfg.stmts.filterIsInstance<EtsIfStmt>().single()
        val (first, second) = graph.successors(loopCondition).take(2).toList()
        val firstToSecond = cfgStatistics.getShortestDistance(method, first, second)
        val secondToFirst = cfgStatistics.getShortestDistance(method, second, first)
        val (bodySuccessor, exitSuccessor) = when {
            firstToSecond != UInt.MAX_VALUE && secondToFirst == UInt.MAX_VALUE -> first to second
            secondToFirst != UInt.MAX_VALUE && firstToSecond == UInt.MAX_VALUE -> second to first
            else -> error("Could not identify loop body/exit successors: $firstToSecond, $secondToFirst")
        }
        val state = stateAt(method, loopCondition, listOf(TsTarget(exitSuccessor)))
        val pruner = pruner()

        assertTrue(pruner.shouldForkTo(state, bodySuccessor), "The loop back-edge path to the target was pruned")
        assertTrue(pruner.shouldForkTo(state, exitSuccessor), "The direct loop-exit path to the target was pruned")
        assertEquals(0, pruner.statistics.unreachablePruned)
    }

    @Test
    fun `terminal removal invalidates cached target distance`() {
        val method = method("criticalFork")
        val ifStatement = method.cfg.stmts.filterIsInstance<EtsIfStmt>().single()
        val targetLocation = graph.successors(ifStatement).first()
        val target = TsTarget(targetLocation)
        val targets = UTargetsSet.from<TsTarget, EtsStmt>(listOf(target))
        val state = stateAt(method, ifStatement, targets)
        val pruner = pruner()

        assertTrue(pruner.shouldForkTo(state, targetLocation))
        assertEquals(1, pruner.statistics.distanceCalculatorsCreated)

        target.propagate(state)
        assertTrue(target.isRemoved)
        assertTrue(pruner.shouldForkTo(state, graph.successors(ifStatement).last()))

        assertEquals(1, pruner.statistics.cacheInvalidations)
        assertEquals(1, pruner.statistics.distanceCalculatorsCreated)
    }

    @Test
    fun `missing and unproved locations are conservative`() {
        val method = method("criticalFork")
        val ifStatement = method.cfg.stmts.filterIsInstance<EtsIfStmt>().single()
        val candidate = graph.successors(ifStatement).last()
        val missingState = stateAt(method, ifStatement, listOf(TsTarget(null)))
        val pruner = pruner()

        assertTrue(pruner.shouldForkTo(missingState, candidate), "A target without a location must disable pruning")
        assertEquals(1, pruner.statistics.missingLocationTargets)

        val foreignLocation = mockk<EtsStmt>(relaxed = true)
        val foreignState = stateAt(method, ifStatement, listOf(TsTarget(foreignLocation)))
        assertTrue(pruner.shouldForkTo(foreignState, candidate), "A foreign target location must be indeterminate")
        assertTrue(pruner.statistics.indeterminateDistances >= 1)
    }

    @Test
    fun `infinity behind a possible call is not treated as proof`() {
        val method = method("callMakesInfinityIndeterminate")
        val ifStatement = method.cfg.stmts.filterIsInstance<EtsIfStmt>().single()
        val successors = graph.successors(ifStatement).take(2).toList()
        val targetSuccessor = successors.minBy(::reachableStatementCount)
        val callSuccessor = successors.single { it !== targetSuccessor }
        val state = stateAt(method, ifStatement, listOf(TsTarget(targetSuccessor)))
        val pruner = pruner()

        assertTrue(
            pruner.shouldForkTo(state, callSuccessor),
            "An under-approximated call graph must not prove infinity",
        )
        assertEquals(0, pruner.statistics.unreachablePruned)
        assertTrue(pruner.statistics.indeterminateDistances >= 1)
    }

    @Test
    fun `machine flag is default off and enabled fixture reduces steps by at least twenty percent`() {
        assertFalse(TsOptions().tsTargetReachabilityPruning)

        val legacy = runPruningFixture(enabled = false)
        val pruned = runPruningFixture(enabled = true)

        assertTrue(legacy.targetReached, "Legacy policy did not reach the target")
        assertTrue(pruned.targetReached, "Pruned policy lost the target")
        assertNull(legacy.statistics, "Disabled flag must restore the legacy fork policy")
        val statistics = assertNotNull(pruned.statistics)
        assertTrue(statistics.unreachablePruned >= 1, "The unreachable fixture did not reject any fork")
        println(
            "tsTargetReachabilityPruning fixture: " +
                "legacySteps=${legacy.steps}, prunedSteps=${pruned.steps}, " +
                "savedSteps=${legacy.steps - pruned.steps}, unreachablePruned=${statistics.unreachablePruned}, " +
                "activeTargetPairs=${statistics.activeTargetPairs}, " +
                "distanceEvaluations=${statistics.distanceEvaluations}",
        )
        assertTrue(
            pruned.steps * 100uL <= legacy.steps * 80uL,
            "Expected >=20% step reduction, legacy=${legacy.steps}, pruned=${pruned.steps}",
        )
    }

    private data class MachineRun(
        val steps: ULong,
        val targetReached: Boolean,
        val statistics: TsTargetReachabilityPruningStatistics?,
    )

    private fun runPruningFixture(enabled: Boolean): MachineRun {
        val method = method("pruningFixture")
        val firstIf = method.cfg.stmts.filterIsInstance<EtsIfStmt>().first()
        val targetSuccessor = graph.successors(firstIf).take(2).minBy(::reachableStatementCount)
        val root = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        val terminal = TsReachabilityTarget.FinalPoint(targetSuccessor)
        if (root.location == firstIf) {
            root.addChild(terminal)
        } else {
            root.addChild(TsReachabilityTarget.IntermediatePoint(firstIf)).addChild(terminal)
        }

        val steps = StepsStatistics<EtsMethod, TsState>()
        val options = UMachineOptions(
            pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            stopOnTargetsReached = false,
            timeout = Duration.INFINITE,
            solverType = SolverType.YICES,
            solverTimeout = Duration.INFINITE,
            typeOperationsTimeout = Duration.INFINITE,
        )
        val machine = TsMachine(
            scene = scene,
            options = options,
            tsOptions = TsOptions(tsTargetReachabilityPruning = enabled),
            machineObserver = CompositeUMachineObserver(ReachabilityObserver(), steps),
        )
        val statistics = machine.use {
            it.analyze(listOf(method), listOf(root))
            it.targetReachabilityPruningStatistics
        }

        return MachineRun(
            steps = steps.totalSteps,
            targetReached = terminal.isRemoved,
            statistics = statistics,
        )
    }

    private fun pruner(): TsTargetReachabilityPruner =
        TsTargetReachabilityPruner(
            applicationGraph = graph,
            cfgStatistics = cfgStatistics,
            callGraphStatistics = PlainCallGraphStatistics(),
        )

    private fun method(name: String): EtsMethod = scene.projectClasses
        .flatMap { it.methods }
        .single { it.name == name }

    private fun stateAt(method: EtsMethod, statement: EtsStmt, targets: List<TsTarget>): TsState =
        stateAt(method, statement, UTargetsSet.from(targets))

    private fun stateAt(
        method: EtsMethod,
        statement: EtsStmt,
        targets: UTargetsSet<TsTarget, EtsStmt>,
    ): TsState = mockk<TsState>().also { state ->
        every { state.targets } returns targets
        every { state.callStack } returns UCallStack(method)
        every { state.currentStatement } returns statement
        every { state.pathNode } returns PathNode.root<EtsStmt>() + statement
    }

    private fun reachableStatementCount(start: EtsStmt): Int {
        val visited = mutableSetOf<EtsStmt>()
        val queue = ArrayDeque<EtsStmt>()
        visited += start
        queue += start
        while (queue.isNotEmpty()) {
            graph.successors(queue.removeFirst()).forEach { successor ->
                if (visited.add(successor)) {
                    queue += successor
                }
            }
        }
        return visited.size
    }
}
