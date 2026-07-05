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
import org.usvm.ts.pbt.interpreter.VArray
import org.usvm.ts.pbt.interpreter.VNumber
import org.usvm.ts.pbt.util.getResourcePath

@EnabledIfEnvironmentVariable(named = "ARKANALYZER_DIR", matches = ".+")
class PbtPhaseTest {

    private val scene: EtsScene by lazy {
        EtsScene(listOf(loadEtsFileAutoConvert(getResourcePath("/pbt/HybridSamples.ts"))))
    }

    private fun method(name: String): EtsMethod =
        scene.projectAndSdkClasses.single { it.name == "HybridSamples" }
            .methods.single { it.name == name }

    @Test
    fun `magic branch is not covered by pbt but the rest is`() {
        val m = method("magic")
        val coverage = CoverageTracker(listOf(m))
        val result = PbtPhase(scene, m, coverage, seed = 42L, maxIterations = 2_000).run()

        assertTrue(result.stats.executions > 0)
        assertTrue(coverage.branchCoverage() < 1.0) { "magic branch should stay uncovered" }

        val uncovered = coverage.uncoveredBranches()
        // Exactly the true-successor of `if (x * 2 === 98764)` should remain
        assertEquals(1, uncovered.size) {
            "expected exactly the magic branch to be uncovered, got: $uncovered"
        }
        // Everything else is covered: 3 of 4 branch edges
        assertEquals(0.75, coverage.branchCoverage(), 1e-9)
    }

    @Test
    fun `crash is found and shrunk`() {
        val m = method("crashy")
        val coverage = CoverageTracker(listOf(m))
        val result = PbtPhase(scene, m, coverage, seed = 7L, maxIterations = 2_000).run()

        assertTrue(result.failures.isNotEmpty()) { "the out-of-bounds crash should be found" }
        val failure = result.failures.first()
        // Shrunk arguments: empty array and index 0 form the minimal failing input
        val arr = failure.shrunkArgs[0]
        val idx = failure.shrunkArgs[1]
        assertTrue(arr is VArray && arr.elements.isEmpty()) { "shrunk array should be empty: $arr" }
        assertTrue(idx is VNumber && (idx.value == 0.0 || idx.value.isNaN())) {
            "shrunk index should be minimal: $idx"
        }
    }

    @Test
    fun `type profile records observed parameter types`() {
        val m = method("anyParam")
        val coverage = CoverageTracker(listOf(m))
        val result = PbtPhase(scene, m, coverage, seed = 1L, maxIterations = 500).run()

        val hints: TsInputTypeHints = result.typeProfiler.toHints()
        val observed = hints.forParameter(m, 0)
        assertTrue(!observed.isNullOrEmpty()) { "type profile for parameter 0 must not be empty" }
        assertTrue(TsHintType.NUMBER in observed!!) { "numbers must be observed among $observed" }
    }

    @Test
    fun `pbt runs are reproducible by seed`() {
        val m = method("magic")
        fun coveredWithSeed(seed: Long): Pair<Int, Int> {
            val coverage = CoverageTracker(listOf(m))
            PbtPhase(scene, m, coverage, seed = seed, maxIterations = 100).run()
            return coverage.coveredStmtCount to coverage.coveredBranchCount
        }
        assertEquals(coveredWithSeed(123L), coveredWithSeed(123L))
    }
}
