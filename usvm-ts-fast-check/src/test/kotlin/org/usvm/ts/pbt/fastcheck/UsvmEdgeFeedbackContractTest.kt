package org.usvm.ts.pbt.fastcheck

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsProjectAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.ts.pbt.FastCheckDiagnosticCode
import org.usvm.ts.pbt.backend.ProjectionCapability
import org.usvm.ts.pbt.backend.ProjectionLevel
import org.usvm.ts.pbt.backend.PropertyCoverageArtifact
import org.usvm.ts.pbt.backend.PropertyCoverageRequest
import org.usvm.ts.pbt.backend.PropertyRunConfiguration
import org.usvm.ts.pbt.backend.PropertyRunResult
import org.usvm.ts.pbt.backend.WitnessNativeReplay
import org.usvm.ts.pbt.backend.WitnessReplayDeadline
import org.usvm.ts.pbt.backend.WitnessTarget
import org.usvm.ts.pbt.backend.captureWitness
import org.usvm.ts.pbt.campaign.campaignSourceSha256
import org.usvm.ts.pbt.manifest.toManifest
import org.usvm.ts.pbt.mapping.PropertyEtsMapper
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.ts.pbt.observation.PropertyObservationSource
import org.usvm.ts.pbt.testResourcesRoot
import org.usvm.ts.pbt.usvm.UsvmCoverageEvidenceStatus
import org.usvm.ts.pbt.usvm.UsvmCoverageFeedback
import org.usvm.ts.pbt.usvm.UsvmEdgeReplayStatus
import org.usvm.ts.pbt.usvm.UsvmPropertySearchResult
import org.usvm.ts.pbt.usvm.UsvmPropertySearchStatus
import org.usvm.ts.pbt.usvm.UsvmPropertySearcher
import java.util.concurrent.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class UsvmEdgeFeedbackContractTest {
    @Test
    fun `only concrete evidence changes the selected pending path for the same scene and symbolic query`() {
        val property = property()
        val positive = backend.run(property, configuration)
        val negative = backend.run(
            property,
            configuration.copy(examples = listOf(listOf(JsConcreteValue.number(-1.0)))),
        )

        val positiveSearch = search(property, positive.coverage) { null }
        val negativeSearch = search(property, negative.coverage) { null }

        val positiveSelection = assertNotNull(positiveSearch.edgeGuidance).selections
            .first { it.pendingStates > 1 && it.distance == 0 }
        val negativeSelection = assertNotNull(negativeSearch.edgeGuidance).selections
            .first { it.pendingStates > 1 && it.distance == 0 }
        assertEquals(false, positiveSelection.goal?.outcome)
        assertEquals(true, negativeSelection.goal?.outcome)
        assertTrue(positiveSelection.goal in positiveSelection.traversedGoals)
        assertTrue(negativeSelection.goal in negativeSelection.traversedGoals)
        assertEquals(setOf(false), positiveSelection.traversedGoals.map { it.outcome }.toSet())
        assertEquals(setOf(true), negativeSelection.traversedGoals.map { it.outcome }.toSet())
        assertEquals(UsvmPropertySearchStatus.NO_VIOLATION_REACHED, positiveSearch.status)
        assertEquals(UsvmPropertySearchStatus.NO_VIOLATION_REACHED, negativeSearch.status)
    }

    @Test
    fun `native single input evidence confirms new edges without invoking the legacy PBT callback`() {
        val property = property()
        val initial = backend.run(property, configuration)
        val deadline = WitnessReplayDeadline(timeoutMillis = 30_000)
        val sources = listOf("guidance-property.ts", "edge-guidance.ts").map { module ->
            PropertyObservationSource(module = module, sha256 = campaignSourceSha256(roots.single().resolve(module)))
        }
        val nativeResults = mutableListOf<WitnessNativeReplay>()

        val result = search(
            property,
            initial.coverage,
            nativeReplay = { inputs ->
                val candidate = property.captureWitness(
                    inputs = inputs,
                    target = WitnessTarget(id = property.predicate.exportName),
                    sources = sources,
                    provenance = "native-coverage-test",
                )
                backend.replayInput(
                    property = property,
                    candidate = candidate,
                    coverageRequest = requireNotNull(configuration.coverageRequest),
                    deadline = deadline,
                ).also(nativeResults::add)
            },
            replay = { error("Native feedback must never fall back to a PBT run") },
        )

        val trace = assertNotNull(result.edgeGuidance)
        assertTrue(nativeResults.isNotEmpty())
        assertTrue(nativeResults.all { it.coverage?.admittedInvocations == 1 }, nativeResults.toString())
        assertTrue(trace.replays.any { it.status == UsvmEdgeReplayStatus.CONFIRMED && it.additions.isNotEmpty() })
        assertTrue(trace.remainingGoals.isEmpty(), trace.toString())
        assertEquals(UsvmPropertySearchStatus.NO_VIOLATION_REACHED, result.status)
    }

    @Test
    fun `a stale native source receipt is retained and cannot remove an edge goal`() {
        val property = property()
        val initial = backend.run(property, configuration)
        val deadline = WitnessReplayDeadline(timeoutMillis = 30_000)

        val result = search(
            property,
            initial.coverage,
            nativeReplay = { inputs ->
                val candidate = property.captureWitness(
                    inputs = inputs,
                    target = WitnessTarget(id = property.predicate.exportName),
                    sources = listOf(
                        PropertyObservationSource(module = property.predicate.module, sha256 = "0".repeat(64)),
                    ),
                    provenance = "stale-native-source-test",
                )
                backend.replayInput(
                    property = property,
                    candidate = candidate,
                    coverageRequest = requireNotNull(configuration.coverageRequest),
                    deadline = deadline,
                )
            },
            replay = { error("A stale native receipt must not fall back to PBT") },
        )

        val trace = assertNotNull(result.edgeGuidance)
        assertEquals(trace.initialGoals, trace.remainingGoals)
        assertTrue(trace.replays.all { it.status == UsvmEdgeReplayStatus.REJECTED_OR_INCOMPATIBLE })
        assertTrue(trace.diagnostics.any { it.contains(FastCheckDiagnosticCode.ENTRY_POINT_SOURCE_HASH_MISMATCH) })
        assertEquals(UsvmPropertySearchStatus.NO_VIOLATION_REACHED, result.status)
    }

    @Test
    fun `an unavailable native operation never falls back to random generation`() {
        val property = property()
        val initial = backend.run(property, configuration)

        val result = search(
            property,
            initial.coverage,
            nativeReplay = { throw IllegalStateException("native source mismatch") },
            replay = { error("Legacy callback must not be used after native failure") },
        )

        val trace = assertNotNull(result.edgeGuidance)
        assertEquals(trace.initialGoals, trace.remainingGoals)
        assertTrue(trace.replays.all { it.status == UsvmEdgeReplayStatus.REPLAY_FAILED })
        assertTrue(trace.diagnostics.any { it.contains("native source mismatch") })
        assertEquals(UsvmPropertySearchStatus.NO_VIOLATION_REACHED, result.status)
    }

    @Test
    fun `native replay cancellation propagates instead of becoming an ordinary replay failure`() {
        val property = property()
        val initial = backend.run(property, configuration)

        assertFailsWith<CancellationException> {
            search(property, initial.coverage) { throw CancellationException("cancelled") }
        }
    }

    @Test
    fun `absent stale unsupported and no-admission evidence keep distinct outcomes and original property search`() {
        val property = property()
        val initial = backend.run(property, configuration)
        val coverage = assertNotNull(initial.coverage)
        val identity = assertNotNull(coverage.identity)
        val stale = coverage.copy(identity = identity.copy(manifestSha256 = "0".repeat(64)))
        val unsupported = coverage.copy(
            files = coverage.files.map { file -> file.copy(branches = file.branches.filter { it.type != "if" }) },
        )
        val variants = listOf(
            null to UsvmCoverageEvidenceStatus.ABSENT,
            stale to UsvmCoverageEvidenceStatus.STALE,
            coverage.copy(identity = null) to UsvmCoverageEvidenceStatus.UNSUPPORTED,
            coverage.copy(admittedInvocations = 0) to UsvmCoverageEvidenceStatus.NO_ADMITTED_INPUTS,
            unsupported to UsvmCoverageEvidenceStatus.UNSUPPORTED,
        )

        for ((evidence, status) in variants) {
            val result = search(property, evidence) { error("No exact goals should request replay") }

            val trace = assertNotNull(result.edgeGuidance)
            assertEquals(status, trace.evidenceStatus)
            assertTrue(trace.initialGoals.isEmpty())
            assertTrue(trace.initialCovered.isEmpty())
            assertTrue(trace.selections.isNotEmpty())
            assertEquals(UsvmPropertySearchStatus.NO_VIOLATION_REACHED, result.status)
        }
    }

    @Test
    fun `failed replay and wrong edge receipt leave the target concretely uncovered`() {
        val property = property()
        val initial = backend.run(property, configuration)
        val coverage = assertNotNull(initial.coverage)

        val failed = search(property, coverage) { throw IllegalStateException("native failure") }
        val mismatched = search(property, coverage) { initial }

        val results = listOf(
            failed to UsvmEdgeReplayStatus.REPLAY_FAILED,
            mismatched to UsvmEdgeReplayStatus.EDGE_MISMATCH,
        )
        for ((result, expectedStatus) in results) {
            val trace = assertNotNull(result.edgeGuidance)
            assertEquals(trace.initialGoals, trace.remainingGoals)
            assertEquals(trace.initialCovered, trace.confirmedCovered)
            assertTrue(trace.replays.isNotEmpty(), trace.toString())
            assertTrue(trace.replays.all { it.status == expectedStatus && it.additions.isEmpty() }, trace.toString())
            assertEquals(UsvmPropertySearchStatus.NO_VIOLATION_REACHED, result.status)
        }
    }

    @Test
    fun `exhausted replay attempts stop guiding without confirming coverage or dropping paths`() {
        val property = property().copy(
            precondition = TypeScriptEntryPoint(module = "guidance-property.ts", exportName = "retryAdmission"),
        )
        val initial = backend.run(property, configuration)

        val result = search(property, initial.coverage) { null }

        val trace = assertNotNull(result.edgeGuidance)
        assertEquals(1, trace.initialGoals.size)
        assertEquals(trace.attemptLimitPerGoal, trace.replays.size)
        assertEquals(trace.initialGoals, trace.remainingGoals)
        assertEquals(trace.initialCovered, trace.confirmedCovered)
        assertTrue(trace.replays.all { it.status == UsvmEdgeReplayStatus.NOT_RUN }, trace.toString())
        assertEquals(trace.replays.size, trace.replays.map { it.inputs }.distinct().size)
        assertEquals(null, trace.selections.last().goal)
        val originalTurns = trace.selections.filterIndexed { index, _ -> index % 2 == 1 }
        assertTrue(originalTurns.isNotEmpty())
        assertTrue(originalTurns.all { it.originalObjectiveTurn }, trace.toString())
        assertEquals(UsvmPropertySearchStatus.NO_VIOLATION_REACHED, result.status)
    }

    @Test
    fun `rejected precondition cannot remove a source edge from the real coverage target queue`() {
        val property = property().copy(
            predicate = TypeScriptEntryPoint(module = "guidance-property.ts", exportName = "guarded"),
            precondition = TypeScriptEntryPoint(module = "guidance-property.ts", exportName = "admission"),
        )
        val initial = backend.run(
            property,
            configuration.copy(
                examples = listOf(listOf(JsConcreteValue.number(-1.0)), listOf(JsConcreteValue.number(1.0))),
            ),
        )

        val result = search(property, assertNotNull(initial.coverage)) { null }

        val trace = assertNotNull(result.edgeGuidance)
        assertEquals(1, initial.numSkips)
        assertEquals(setOf(false), trace.initialCovered.map { it.outcome }.toSet())
        assertEquals(setOf(true), trace.initialGoals.map { it.outcome }.toSet())
        assertEquals(trace.initialGoals, trace.remainingGoals)
        assertTrue(
            trace.replays.isEmpty(),
            "The source edge reached only in the guard cannot confirm predicate coverage",
        )
        assertEquals(UsvmPropertySearchStatus.NO_VIOLATION_REACHED, result.status)
    }

    @Test
    fun `caller deadline stops before any symbolic queue selection`() {
        val property = property()
        val manifest = property.toManifest()
        val mapping = PropertyEtsMapper(scene = scene, sourceRoots = roots).map(manifest)
        val searcher = UsvmPropertySearcher(
            scene = scene,
            machineOptions = UMachineOptions(solverType = SolverType.YICES, timeout = 10.seconds),
            deadlineReached = { true },
        )

        val result = searcher.search(
            manifest = manifest,
            mapping = mapping,
            concreteCapability = ProjectionCapability(level = ProjectionLevel.EXACT),
            coverageFeedback = UsvmCoverageFeedback(initialCoverage = null) { error("Deadline forbids replay") },
        )

        assertEquals(UsvmPropertySearchStatus.TIMEOUT, result.status)
        assertTrue(assertNotNull(result.edgeGuidance).selections.isEmpty())
    }

    private fun search(
        property: PropertyDefinition,
        coverage: PropertyCoverageArtifact?,
        nativeReplay: ((List<JsConcreteValue>) -> WitnessNativeReplay?)? = null,
        replay: (List<JsConcreteValue>) -> PropertyRunResult?,
    ): UsvmPropertySearchResult {
        val manifest = property.toManifest()
        val mapping = PropertyEtsMapper(scene = scene, sourceRoots = roots).map(manifest, coverage)
        return UsvmPropertySearcher(
            scene = scene,
            machineOptions = UMachineOptions(solverType = SolverType.YICES, timeout = 10.seconds),
        ).search(
            manifest = manifest,
            mapping = mapping,
            concreteCapability = ProjectionCapability(level = ProjectionLevel.EXACT),
            coverageFeedback = UsvmCoverageFeedback(
                initialCoverage = coverage,
                nativeReplay = nativeReplay,
                replay = replay,
            ),
        )
    }

    private fun property() = PropertyDefinition(
        id = PropertyId("edge-feedback.contract"),
        inputs = listOf(PropertyInput(name = "value", domain = IntegerDomain(min = -100, max = 100))),
        predicate = TypeScriptEntryPoint(module = "guidance-property.ts", exportName = "valid"),
    )

    private companion object {
        val roots = listOf(testResourcesRoot().resolve("properties/dsl"))
        val backend = FastCheckBackend(sourceRoots = roots)
        val scene: EtsScene by lazy { loadEtsProjectAutoConvert(roots.single(), provider = EtsIrProvider.TS_FRONTEND) }
        val configuration = PropertyRunConfiguration(
            seed = 42,
            numRuns = 1,
            timeoutMillis = 10_000,
            examples = listOf(listOf(JsConcreteValue.number(1.0))),
            coverageRequest = PropertyCoverageRequest(admittedOnly = true),
        )
    }
}
