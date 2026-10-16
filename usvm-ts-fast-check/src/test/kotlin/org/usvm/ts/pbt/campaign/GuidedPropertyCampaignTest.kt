package org.usvm.ts.pbt.campaign

import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.backend.CoverageScope
import org.usvm.ts.pbt.backend.PropertyCoverageRequest
import org.usvm.ts.pbt.backend.PropertyFailureKind
import org.usvm.ts.pbt.backend.PropertyInputExportRequest
import org.usvm.ts.pbt.backend.PropertyRunConfiguration
import org.usvm.ts.pbt.backend.PropertyRunStatus
import org.usvm.ts.pbt.backend.WitnessReductionStatus
import org.usvm.ts.pbt.backend.WitnessReplayStatus
import org.usvm.ts.pbt.model.ExecutionKind
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.ts.pbt.observation.PropertyObservationSource
import org.usvm.ts.pbt.testResourcesRoot
import org.usvm.ts.pbt.usvm.UsvmEdgeReplayStatus
import org.usvm.ts.pbt.usvm.UsvmPropertySearchStatus
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GuidedPropertyCampaignTest {
    private val root = testResourcesRoot().resolve("properties/campaign")
    private val runner = PropertyCampaignRunner(sourceRoot = root)
    private val configuration = PropertyRunConfiguration(seed = 355, numRuns = 1, timeoutMillis = 60_000)

    @Test
    fun `one real PBT batch changes the next symbolic work for the same original property`() {
        val positive = run(exportName = "branchHolds", examples = listOf(1))
        val negative = run(exportName = "branchHolds", examples = listOf(-1))

        val positiveTrace = assertNotNull(positive.result.symbolic?.edgeGuidance)
        val negativeTrace = assertNotNull(negative.result.symbolic?.edgeGuidance)
        val positiveChoice = positiveTrace.selections.first { it.pendingStates > 1 && it.distance == 0 }
        val negativeChoice = negativeTrace.selections.first { it.pendingStates > 1 && it.distance == 0 }
        assertEquals(false, positiveChoice.goal?.outcome)
        assertEquals(true, negativeChoice.goal?.outcome)
        assertTrue(positiveTrace.replays.any { it.status == UsvmEdgeReplayStatus.CONFIRMED })
        assertTrue(negativeTrace.replays.any { it.status == UsvmEdgeReplayStatus.CONFIRMED })
        assertTrue(positiveTrace.remainingGoals.isEmpty())
        assertTrue(negativeTrace.remainingGoals.isEmpty())
        assertEquals(1, positive.result.concrete?.numRuns)
        val observations = assertNotNull(positive.result.concrete?.observations)
        assertEquals(1, observations.invocations.size)
        assertEquals(UsvmPropertySearchStatus.NO_VIOLATION_REACHED, positive.result.symbolic?.status)
    }

    @Test
    fun `covered prefixes remain traversable for an uncovered nested edge`() {
        val artifact = run(exportName = "nestedHolds", examples = listOf(1))

        val trace = assertNotNull(artifact.result.symbolic?.edgeGuidance)
        val missingNested = trace.initialGoals.single { it.outcome }
        assertTrue(trace.replays.any { missingNested in it.additions }, trace.toString())
        assertTrue(trace.remainingGoals.isEmpty(), trace.toString())
        assertEquals(UsvmPropertySearchStatus.NO_VIOLATION_REACHED, artifact.result.symbolic?.status)
    }

    @Test
    fun `compact export supplies native inputs without allocating automatic point observations`() {
        val configuration = configuration.copy(
            examples = examples(1),
            inputExportRequest = PropertyInputExportRequest(
                sources = listOf(
                    PropertyObservationSource(module = MODULE, sha256 = campaignSourceSha256(root.resolve(MODULE)))
                ),
            ),
        )

        val artifact = runner.runArtifact(
            property = property(exportName = "branchHolds"),
            mode = PropertyCampaignMode.COVERAGE_FEEDBACK,
            configuration = configuration,
            pbtAllocationMillis = 15_000,
        )

        val concrete = assertNotNull(artifact.result.concrete)
        assertEquals(1, concrete.inputExport?.records?.size)
        assertNull(concrete.observations)
        assertNotNull(artifact.result.symbolic?.edgeGuidance)
        val concreteCoverage = assertNotNull(concrete.coverage)
        assertTrue(concreteCoverage.provenance.request.admittedOnly)
    }

    @Test
    fun `saturated coverage still finds and natively confirms an original defect and saves the full trace`() {
        val artifact = run(exportName = "branchDifferent", examples = listOf(-1, 3), shrinkWitness = false)
        val path = Files.createTempFile("guided-campaign-", ".json")

        try {
            artifact.write(path)
            val restored = PropertyCampaignArtifact.read(path)

            val symbolic = assertNotNull(restored.result.symbolic)
            val trace = assertNotNull(symbolic.edgeGuidance)
            assertTrue(trace.initialGoals.isEmpty(), trace.toString())
            assertTrue(trace.selections.isNotEmpty())
            assertTrue(trace.selections.all { it.originalObjectiveTurn })
            assertEquals(UsvmPropertySearchStatus.VIOLATION_REACHED, symbolic.status)
            assertEquals(WitnessReplayStatus.CONFIRMED_PROPERTY_VIOLATION, restored.result.confirmation?.status)
            assertEquals(WitnessReductionStatus.DISABLED, restored.result.confirmation?.reduction?.status)
            assertEquals(artifact.result.symbolic?.edgeGuidance, trace)
            assertEquals(artifact.result.symbolic?.diagnostics, symbolic.diagnostics)
            assertEquals(15_000L, restored.pbtAllocationMillis)
            assertTrue(restored.result.confirmedViolation)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun `sequential retains the no guidance control with the same explicit allocation`() {
        val artifact = runner.runArtifact(
            property = property(exportName = "branchHolds"),
            mode = PropertyCampaignMode.SEQUENTIAL,
            configuration = configuration.copy(examples = examples(1)),
            pbtAllocationMillis = 15_000,
        )

        assertEquals(PropertyRunStatus.SUCCESS, artifact.result.concrete?.status)
        assertNull(artifact.result.symbolic?.edgeGuidance)
        assertEquals(UsvmPropertySearchStatus.NO_VIOLATION_REACHED, artifact.result.symbolic?.status)
    }

    @Test
    fun `PBT phase timeout leaves an explicit diagnostic and does not consume the symbolic remainder`() {
        val property = property(exportName = "delayedHolds").copy(
            predicate = TypeScriptEntryPoint(
                module = MODULE,
                exportName = "delayedHolds",
                executionKind = ExecutionKind.ASYNC,
            ),
        )

        val artifact = runner.runArtifact(
            property = property,
            mode = PropertyCampaignMode.COVERAGE_FEEDBACK,
            configuration = configuration.copy(timeoutMillis = 30_000),
            pbtAllocationMillis = 1_000,
        )

        val concrete = artifact.result.concrete
        if (concrete != null) assertEquals(PropertyFailureKind.TIMEOUT, concrete.failure?.kind)
        assertEquals(UsvmPropertySearchStatus.UNSUPPORTED, artifact.result.symbolic?.status)
        assertTrue(artifact.result.diagnostic?.startsWith("PBT allocation") == true)
        assertEquals(PropertyCampaignStop.COMPLETED, artifact.result.stop)
    }

    @Test
    fun `runtime limitation reason and source survive the ordinary artifact roundtrip`() {
        val property = property(exportName = "growthLimit").copy(
            inputs = listOf(PropertyInput(name = "value", domain = IntegerDomain(min = 1000, max = 1000))),
        )
        val artifact = runner.runArtifact(
            property = property,
            mode = PropertyCampaignMode.SYMBOLIC_ONLY,
            configuration = configuration,
        )
        val path = Files.createTempFile("runtime-limitation-campaign-", ".json")

        try {
            artifact.write(path)
            val restored = PropertyCampaignArtifact.read(path)

            assertEquals(UsvmPropertySearchStatus.UNSUPPORTED, restored.result.symbolic?.status)
            val diagnostic = assertNotNull(restored.result.symbolic).diagnostics
                .single { it.code == "usvm.runtime.array-index-growth" }
            assertTrue(diagnostic.message.contains("array index write"), diagnostic.message)
            assertTrue(diagnostic.path.contains(MODULE), diagnostic.toString())
            assertEquals(artifact.result.symbolic?.diagnostics, restored.result.symbolic?.diagnostics)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    private fun run(
        exportName: String,
        examples: List<Int>,
        shrinkWitness: Boolean = true,
    ) = runner.runArtifact(
        property = property(exportName),
        mode = PropertyCampaignMode.COVERAGE_FEEDBACK,
        configuration = configuration.copy(
            numRuns = examples.size,
            examples = examples.map { listOf(JsConcreteValue.number(it.toDouble())) },
            coverageRequest = PropertyCoverageRequest(scopes = setOf(CoverageScope.SOURCE_UNDER_TEST)),
        ),
        pbtAllocationMillis = 15_000,
        shrinkWitness = shrinkWitness,
    )

    private fun property(exportName: String) = PropertyDefinition(
        id = PropertyId("campaign.guided.$exportName"),
        inputs = listOf(PropertyInput(name = "value", domain = IntegerDomain(min = -100, max = 100))),
        predicate = TypeScriptEntryPoint(module = MODULE, exportName = exportName),
    )

    private fun examples(value: Int) = listOf(listOf(JsConcreteValue.number(value.toDouble())))

    private companion object {
        const val MODULE = "GuidedProperties.ts"
    }
}
