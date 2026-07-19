package org.usvm.ts.pbt.hybrid

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.usvm.machine.TsHintType
import org.usvm.machine.TsInputTypeHints
import org.usvm.ts.pbt.coverage.CoverageTracker
import org.usvm.ts.pbt.external.stableBranchId
import org.usvm.ts.pbt.external.stableMethodId
import org.usvm.ts.pbt.util.getResourcePath
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@EnabledIfEnvironmentVariable(named = "ARKANALYZER_DIR", matches = ".+")
class HybridE2eTest {

    private val scene: EtsScene by lazy {
        EtsScene(listOf(loadEtsFileAutoConvert(getResourcePath("/pbt/HybridSamples.ts"))))
    }

    private fun method(name: String): EtsMethod =
        scene.projectAndSdkClasses.single { it.name == "HybridSamples" }
            .methods.single { it.name == name }

    @Test
    fun `hybrid pipeline reaches the magic branch and confirms it by replay`() {
        val m = method("magic")
        val coverage = CoverageTracker(listOf(m))

        // Phase 1: PBT
        val pbt = PbtPhase(scene, m, coverage, seed = 42L, maxIterations = 2_000).run()
        assertEquals(0.75, coverage.branchCoverage(), 1e-9) { "PBT must leave only the magic branch" }

        // Phase 2: targeted symbolic execution on the leftovers
        val symbolic = SymbolicPhase(
            scene = scene,
            method = m,
            coverage = coverage,
            hints = pbt.typeProfiler.toHints(),
        ).run()

        assertEquals(1, symbolic.outcomes.size)
        assertEquals(1, symbolic.machineRuns) { "legacy monolithic accounting must stay unchanged" }
        val outcome = symbolic.outcomes.single()
        assertTrue(outcome.reached) { "symbolic phase must reach the magic branch" }
        assertTrue(outcome.replayConfirmed) { "the synthesized input must replay concretely" }
        assertEquals(1.0, coverage.branchCoverage(), 1e-9) { "hybrid coverage must be complete" }
        assertNull(symbolic.shardStatistics) { "default flags must preserve the legacy monolithic path" }

        // The synthesized input is the actual solution of x * 2 === 98764
        val inputs = outcome.inputs!!
        val x = inputs.parameters.first()
        assertEquals(
            49_382.0,
            (x as org.usvm.api.TsTestValue.TsNumber).number,
            1e-9,
        )
    }

    @Test
    fun `type hints reduce symbolic effort on untyped parameters`() {
        val m = method("manyUntyped")

        data class Run(val steps: ULong, val reached: Int, val wallMs: Long)

        fun runSymbolicOnly(hints: TsInputTypeHints): Run {
            val coverage = CoverageTracker(listOf(m))
            // No PBT: target every branch from scratch, measure total steps
            val result = SymbolicPhase(
                scene = scene,
                method = m,
                coverage = coverage,
                hints = hints,
                hintFallback = false,
            ).run()
            return Run(
                steps = result.steps,
                reached = result.outcomes.count { it.reached },
                wallMs = result.wallMs,
            )
        }

        val numberHints = TsInputTypeHints(
            mapOf(
                TsInputTypeHints.keyOf(m) to mapOf(
                    0 to setOf(TsHintType.NUMBER),
                    1 to setOf(TsHintType.NUMBER),
                    2 to setOf(TsHintType.NUMBER),
                ),
            ),
        )

        val withHints = runSymbolicOnly(numberHints)
        val withoutHints = runSymbolicOnly(TsInputTypeHints.EMPTY)

        // NOTE: machine *steps* are typically equal in both modes on micro-fixtures:
        // with `useSolverForForks` infeasible discriminator forks are pruned eagerly,
        // so hints shift the cost into solver calls, visible as wall time on
        // larger corpora (measured by the CLI experiments, not asserted here).
        println(
            "[ablation] with hints: steps=${withHints.steps}, reached=${withHints.reached}, " +
                "wallMs=${withHints.wallMs}; without: steps=${withoutHints.steps}, " +
                "reached=${withoutHints.reached}, wallMs=${withoutHints.wallMs}"
        )

        // All branches are number-reachable, so hints must not lose any of them
        assertTrue(withHints.reached >= withoutHints.reached) {
            "hinted run must reach at least as many branches"
        }
        assertTrue(withHints.reached > 0) { "hinted run must reach some branches" }
        assertTrue(withHints.steps <= withoutHints.steps) {
            "hints must not increase the search effort: ${withHints.steps} > ${withoutHints.steps}"
        }
    }

    @Test
    fun `independent target chains never share their entry root`() {
        val m = method("magic")
        val branches = CoverageTracker(listOf(m)).uncoveredBranches()

        val chains = buildIndependentTargetChains(m, branches)

        assertEquals(branches.size, chains.size)
        chains.forEachIndexed { index, chain ->
            chains.drop(index + 1).forEach { other -> assertTrue(chain.root !== other.root) }
        }
        chains.forEach { chain ->
            assertTrue(chain.root.location === m.cfg.stmts.first())
            val intermediate = chain.root.children.single()
            assertTrue(intermediate.location === chain.branch.edge.ifStmt)
            assertTrue(intermediate.children.single() === chain.terminal)
            assertTrue(chain.terminal.location === chain.branch.edge.successor)
        }
    }

    @Test
    fun `public planner keeps methods separate and IDs stable under permutation`() {
        val methods = listOf(method("magic"), method("anyParam"))
        val branches = CoverageTracker(methods).uncoveredBranches()
        val planner = TargetShardPlanner(TargetShardSize.FOUR)

        fun layout(input: List<CoverageTracker.UncoveredBranch>): List<Pair<String, List<String>>> =
            planner.plan(input).map { shard ->
                assertTrue(shard.targets.all { it.method === shard.method })
                shard.id.value to shard.targets.map { target ->
                    stableBranchId(target.method, target.edge.ifStmt, target.edge.successor)
                }
            }

        val ordered = layout(branches)
        val permuted = layout(branches.shuffled(kotlin.random.Random(73)))

        assertEquals(ordered, permuted)
        val methodOrder = planner.plan(branches).map { stableMethodId(it.method) }
        assertEquals(methodOrder.sorted(), methodOrder)
    }

    @Test
    fun `replay pruning reduces one-target machine runs without coverage loss`() {
        val m = method("magic")

        data class Run(val result: SymbolicPhaseResult, val coverage: CoverageTracker)

        fun run(replayPrune: Boolean): Run {
            val coverage = CoverageTracker(listOf(m))
            val result = SymbolicPhase(
                scene = scene,
                method = m,
                coverage = coverage,
                hintFallback = false,
                perTargetTimeout = 5.seconds,
                orchestration = SymbolicOrchestrationConfig(
                    targetShardSize = TargetShardSize.ONE,
                    replayPruneBetweenShards = replayPrune,
                ),
            ).run()
            return Run(result, coverage)
        }

        val unpruned = run(replayPrune = false)
        val pruned = run(replayPrune = true)

        assertEquals(unpruned.coverage.coveredBranchCount, pruned.coverage.coveredBranchCount)
        assertEquals(1.0, pruned.coverage.branchCoverage(), 1e-9)
        assertTrue(pruned.result.machineRuns < unpruned.result.machineRuns) {
            "expected incidental replay coverage to save a run: " +
                "unpruned=${unpruned.result.machineRuns}, pruned=${pruned.result.machineRuns}"
        }
        val metrics = requireNotNull(pruned.result.shardStatistics)
        assertEquals(pruned.result.machineRuns, metrics.machineRuns)
        assertTrue(metrics.replayPrunedTargets >= 1)
        assertTrue(metrics.runs.map { it.shardId }.distinct().size == metrics.runs.size)
        val savedRuns = unpruned.result.machineRuns - pruned.result.machineRuns
        println(
            "targetShard synthetic: unprunedRuns=${unpruned.result.machineRuns}, " +
                "prunedRuns=${pruned.result.machineRuns}, saved=$savedRuns, " +
                "covered=${pruned.coverage.coveredBranchCount}/${pruned.coverage.allBranches.size}",
        )
    }

    @Test
    fun `hint-free fallback is independently sharded after a restrictive profile`() {
        val m = method("anyParam")
        val coverage = CoverageTracker(listOf(m))
        val booleanOnly = TsInputTypeHints(
            mapOf(TsInputTypeHints.keyOf(m) to mapOf(0 to setOf(TsHintType.BOOLEAN))),
        )

        val result = SymbolicPhase(
            scene = scene,
            method = m,
            coverage = coverage,
            hints = booleanOnly,
            hintFallback = true,
            perTargetTimeout = 2.seconds,
            orchestration = SymbolicOrchestrationConfig(
                targetShardSize = TargetShardSize.ONE,
                replayPruneBetweenShards = true,
                symbolicProgressStop = true,
                progressTimeout = 300.milliseconds,
                tsTargetReachabilityPruning = true,
            ),
        ).run()

        assertEquals(1.0, coverage.branchCoverage(), 1e-9)
        assertTrue(result.outcomes.any { it.fallbackUsed && it.replayConfirmed })
        val metrics = requireNotNull(result.shardStatistics)
        assertTrue(metrics.runs.any { it.attempt == SymbolicAttemptKind.FALLBACK && it.machineStarted })
        assertEquals(result.machineRuns, metrics.machineRuns)
    }
}
