package org.usvm.ts.pbt.hybrid

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.usvm.ts.pbt.report.HybridReport
import org.usvm.ts.pbt.util.getResourcePath
import kotlin.time.Duration.Companion.seconds

@EnabledIfEnvironmentVariable(named = "ARKANALYZER_DIR", matches = ".+")
class HybridAnalyzerTest {

    private val scene: EtsScene by lazy {
        EtsScene(listOf(loadEtsFileAutoConvert(getResourcePath("/pbt/HybridSamples.ts"))))
    }

    private fun method(name: String): EtsMethod =
        scene.projectAndSdkClasses.single { it.name == "HybridSamples" }
            .methods.single { it.name == name }

    private fun config(mode: AnalysisMode) = HybridConfig(
        mode = mode,
        seed = 42L,
        pbtMaxIterations = 1_000,
        perTargetTimeout = 20.seconds,
    )

    @Test
    fun `all four modes produce consistent reports on magic`() {
        val m = method("magic")

        val pbtOnly = HybridAnalyzer(scene, config(AnalysisMode.PBT_ONLY)).analyzeMethod(m)
        assertNotNull(pbtOnly.pbt)
        assertNull(pbtOnly.symbolic)
        assertEquals(0.75, pbtOnly.branchCoverage, 1e-9)

        val symbolicOnly = HybridAnalyzer(scene, config(AnalysisMode.SYMBOLIC_ONLY)).analyzeMethod(m)
        assertNull(symbolicOnly.pbt)
        assertNotNull(symbolicOnly.symbolic)
        assertEquals(1.0, symbolicOnly.branchCoverage, 1e-9)

        val hybrid = HybridAnalyzer(scene, config(AnalysisMode.HYBRID)).analyzeMethod(m)
        assertEquals(1.0, hybrid.branchCoverage, 1e-9)
        // PBT covered 3 of 4 edges, so only one symbolic target remains
        assertEquals(1, hybrid.symbolic!!.targets.size)
        assertTrue(hybrid.symbolic!!.targets.single().reached)
        assertTrue(hybrid.symbolic!!.targets.none { it.hintsUsed })

        val hybridHints = HybridAnalyzer(scene, config(AnalysisMode.HYBRID_WITH_HINTS)).analyzeMethod(m)
        assertEquals(1.0, hybridHints.branchCoverage, 1e-9)
        assertTrue(hybridHints.typeProfile.isNotEmpty()) { "type profile must be reported" }
    }

    @Test
    fun `report json round-trip`() {
        val report = HybridAnalyzer(scene, config(AnalysisMode.HYBRID_WITH_HINTS))
            .analyze(listOf(method("magic"), method("crashy")))

        val text = HybridReport.encode(report)
        val decoded = HybridReport.decode(text)
        assertEquals(report, decoded)

        val crashy = decoded.methods.single { "crashy" in it.method }
        assertTrue(crashy.pbt!!.failures.isNotEmpty()) { "crashy failures must be serialized" }
        assertTrue(decoded.methods.all { it.timeline.isNotEmpty() })
    }
}
