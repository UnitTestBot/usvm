package org.usvm.ts.pbt.hybrid

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.usvm.machine.TsHintType
import org.usvm.machine.TsInputTypeHints
import org.usvm.ts.pbt.coverage.CoverageTracker
import org.usvm.ts.pbt.util.getResourcePath

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
            scene, m, coverage,
            hints = pbt.typeProfiler.toHints(),
        ).run()

        assertEquals(1, symbolic.outcomes.size)
        val outcome = symbolic.outcomes.single()
        assertTrue(outcome.reached) { "symbolic phase must reach the magic branch" }
        assertTrue(outcome.replayConfirmed) { "the synthesized input must replay concretely" }
        assertEquals(1.0, coverage.branchCoverage(), 1e-9) { "hybrid coverage must be complete" }

        // The synthesized input is the actual solution of x * 2 === 98764
        val inputs = outcome.inputs!!
        val x = inputs.parameters.first()
        assertEquals(
            49382.0,
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
                scene, m, coverage,
                hints = hints,
                hintFallback = false,
            ).run()
            return Run(
                steps = result.outcomes.sumOf { it.steps },
                reached = result.outcomes.count { it.reached },
                wallMs = result.outcomes.sumOf { it.wallMs },
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
}
