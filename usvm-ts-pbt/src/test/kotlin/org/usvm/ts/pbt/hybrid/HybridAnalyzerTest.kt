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
import org.usvm.ts.pbt.external.ExternalCorpusInputProvider
import org.usvm.ts.pbt.external.ExternalTestCase
import org.usvm.ts.pbt.external.ExternalTestCorpus
import org.usvm.ts.pbt.external.ExternalValue
import org.usvm.ts.pbt.external.stableMethodId
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

    @Test
    fun `external corpus covers the ordinary magic edges before one targeted symbolic run`() {
        val m = method("magic")
        val methodId = stableMethodId(m)
        val provider = ExternalCorpusInputProvider.fromCorpus(
            ExternalTestCorpus(
                producer = "fixture@1",
                cases = listOf(-1, 1).map { value ->
                    ExternalTestCase(
                        id = "x-$value",
                        methodId = methodId,
                        arguments = listOf(ExternalValue("number", value = value.toString())),
                    )
                },
            )
        )
        val externalOnly = config(AnalysisMode.HYBRID).copy(
            pbtMaxIterations = 0,
            externalInputProviders = listOf(provider),
            internalPbtEnabled = false,
        )

        val report = HybridAnalyzer(scene, externalOnly).analyzeMethod(m)

        assertEquals(2, report.pbt!!.externalImported)
        assertEquals(2, report.pbt!!.externalExecuted)
        assertEquals(0, report.pbt!!.generatedExecutions)
        assertEquals(1, report.symbolic!!.targets.size)
        assertTrue(report.symbolic!!.targets.single().reached)
        assertTrue(report.symbolic!!.targets.single().replayConfirmed)
        assertEquals(1.0, report.branchCoverage, 1e-9)
    }

    @Test
    fun `unconstrained generic array parameters have satisfiable symbolic inputs`() {
        val report = HybridAnalyzer(scene, config(AnalysisMode.SYMBOLIC_ONLY).copy(perTargetTimeout = 5.seconds))
            .analyzeMethod(method("genericSwap"))

        assertEquals(1.0, report.branchCoverage, 1e-9)
        assertTrue(report.symbolic!!.targets.all { it.reached })
        assertTrue(report.symbolic!!.targets.all { it.replayConfirmed })
    }
}
