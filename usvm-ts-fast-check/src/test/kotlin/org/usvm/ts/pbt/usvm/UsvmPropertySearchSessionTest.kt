package org.usvm.ts.pbt.usvm

import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.loadEtsProjectAutoConvert
import org.junit.jupiter.api.Test
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.ts.pbt.backend.ProjectionCapability
import org.usvm.ts.pbt.backend.ProjectionLevel
import org.usvm.ts.pbt.backend.PropertyCoverageArtifact
import org.usvm.ts.pbt.backend.PropertyCoverageRequest
import org.usvm.ts.pbt.backend.PropertyFailureKind
import org.usvm.ts.pbt.backend.PropertyRunConfiguration
import org.usvm.ts.pbt.fastcheck.FastCheckBackend
import org.usvm.ts.pbt.manifest.toManifest
import org.usvm.ts.pbt.mapping.PropertyEtsMapper
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.ts.pbt.testResourcesRoot
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class UsvmPropertySearchSessionTest {
    @Test
    fun `native coverage between advances changes the existing pending queue for the original predicate`() {
        val positive = resumeWithNativeEvidence(value = 1)
        val negative = resumeWithNativeEvidence(value = -1)

        val positivePrefix = positive.first.selections.map { Triple(it.statement, it.pendingStates, it.goal) }
        val negativePrefix = negative.first.selections.map { Triple(it.statement, it.pendingStates, it.goal) }
        assertEquals(positivePrefix, negativePrefix)
        assertNotEquals(positive.selectedGoal, negative.selectedGoal)
        assertEquals(positive.first.initialGoals, negative.first.initialGoals)
    }

    @Test
    fun `stale incompatible and rejected-input artifacts leave the retained analysis usable`() {
        val initial = initialCoverage()
        val identity = assertNotNull(initial.identity)
        val rejected = listOf(
            initial.copy(identity = identity.copy(manifestSha256 = "0".repeat(64))) to UsvmCoverageUpdateStatus.STALE,
            initial.copy(provenance = initial.provenance.copy(runtimeVersion = "different")) to
                UsvmCoverageUpdateStatus.INCOMPATIBLE,
            initial.copy(admittedInvocations = 0) to UsvmCoverageUpdateStatus.NO_ADMITTED_INPUTS,
            initial.copy(identity = null) to UsvmCoverageUpdateStatus.UNSUPPORTED,
        )

        openSession(initial).use { session ->
            val first = session.advance(maxSteps = 1uL)
            for ((artifact, status) in rejected) {
                val update = session.applyCoverage(artifact)

                assertEquals(status, update.status, first.edgeGuidance.toString())
                assertTrue(update.additions.isEmpty())
                assertFalse(session.isFinished)
            }
            val next = session.advance(maxSteps = 1uL)

            assertEquals(UsvmPropertySearchStatus.IN_PROGRESS, first.status)
            assertEquals(UsvmPropertySearchStatus.IN_PROGRESS, next.status)
            val firstTrace = assertNotNull(first.edgeGuidance)
            val nextTrace = assertNotNull(next.edgeGuidance)
            assertEquals(firstTrace.selections, nextTrace.selections.take(firstTrace.selections.size))
            assertEquals(firstTrace.confirmedCovered, nextTrace.confirmedCovered)
            assertEquals(UsvmPropertySearchStatus.NO_VIOLATION_REACHED, session.advance().status)
        }
    }

    @Test
    fun `caller collection mutations cannot replace the original property or native environment`() {
        val initial = initialCoverage()
        val sourceRoots = initial.provenance.sourceRoots.toMutableList()
        val patterns = initial.provenance.request.includePatterns.toMutableList()
        val anchor = initial.copy(
            provenance = initial.provenance.copy(
                sourceRoots = sourceRoots,
                request = initial.provenance.request.copy(includePatterns = patterns),
            ),
        )
        val inputs = property.inputs.toMutableList()
        val definition = property.copy(inputs = inputs)

        openSession(anchor, definition = definition).use { session ->
            sourceRoots += "changed-by-caller"
            patterns += "**/changed-by-caller"
            inputs.clear()
            val update = session.applyCoverage(initial)
            val changed = session.applyCoverage(anchor)
            val progress = session.advance(maxSteps = 1uL)

            assertEquals(UsvmCoverageUpdateStatus.ACCEPTED, update.status)
            assertEquals(UsvmCoverageUpdateStatus.INCOMPATIBLE, changed.status)
            assertEquals(UsvmPropertySearchStatus.IN_PROGRESS, progress.status)
            assertEquals(UsvmPropertySearchStatus.NO_VIOLATION_REACHED, session.advance().status)
        }
    }

    @Test
    fun `an unsupported completed path leaves a partial phase in progress until pending work finishes`() {
        val definition = property.copy(
            predicate = TypeScriptEntryPoint(module = MODULE, exportName = "partialUnsupported"),
        )
        val initial = assertNotNull(backend.run(definition, configuration).coverage)

        openSession(initial, definition = definition).use { session ->
            var progress = session.advance(maxSteps = 0uL)
            var observedUnsupported = false
            var steps = 0
            while (!observedUnsupported && !session.isFinished && steps < 200) {
                progress = session.advance(maxSteps = 1uL)
                observedUnsupported = progress.diagnostics.any { it.code == "usvm.execution.unsupported" }
                steps += 1
            }

            assertTrue(observedUnsupported, progress.toString())
            assertFalse(session.isFinished, progress.toString())
            assertEquals(UsvmPropertySearchStatus.IN_PROGRESS, progress.status)
            assertEquals(UsvmPropertySearchStatus.UNSUPPORTED, session.advance().status)
            assertTrue(session.isFinished)
        }
    }

    @Test
    fun `a partial phase retains its reached violation while another path remains pending`() {
        val definition = property.copy(predicate = TypeScriptEntryPoint(module = MODULE, exportName = "earlyViolation"))
        val initial = assertNotNull(backend.run(definition, configuration).coverage)

        openSession(initial, definition = definition, stopOnTargetsReached = false).use { session ->
            var progress = session.advance(maxSteps = 0uL)
            var steps = 0
            while (progress.inputs == null && !session.isFinished && steps < 200) {
                progress = session.advance(maxSteps = 1uL)
                steps += 1
            }

            val candidate = assertNotNull(progress.inputs, progress.toString())
            assertEquals(UsvmPropertySearchStatus.IN_PROGRESS, progress.status)
            assertEquals(UsvmPropertyViolationTarget.PREDICATE_FALSE, progress.target)
            assertFalse(session.isFinished)
            val next = session.advance(maxSteps = 1uL)
            val replay = backend.run(
                property = definition,
                configuration = configuration.copy(examples = listOf(candidate), exactReplay = true),
            )

            assertEquals(candidate, next.inputs)
            assertEquals(progress.target, next.target)
            assertEquals(PropertyFailureKind.PROPERTY, replay.failure?.kind)
            assertEquals(UsvmPropertySearchStatus.VIOLATION_REACHED, session.advance().status)
        }
    }

    @Test
    fun `a current source change invalidates reuse instead of merely rejecting a stale report`() {
        for (applyFirst in listOf(false, true)) {
            withCopiedSources { root ->
                val roots = listOf(root)
                val backend = FastCheckBackend(sourceRoots = roots)
                val scene = loadEtsProjectAutoConvert(root, provider = EtsIrProvider.TS_FRONTEND)
                val initial = assertNotNull(backend.run(property, configuration).coverage)
                val source = root.resolve(MODULE)
                val original = Files.readString(source)

                openSession(initial, scene, roots).use { session ->
                    val first = session.advance(maxSteps = 1uL)
                    Files.writeString(source, original + "\n// changed after analysis preparation\n")
                    if (applyFirst) {
                        assertEquals(UsvmCoverageUpdateStatus.SOURCE_CHANGED, session.applyCoverage(initial).status)
                    }
                    val invalidated = session.advance(maxSteps = 1uL)
                    Files.writeString(source, original)
                    val afterRestore = session.advance()

                    assertEquals(UsvmPropertySearchStatus.SOURCE_INVALIDATED, invalidated.status)
                    assertEquals(UsvmPropertySearchStatus.SOURCE_INVALIDATED, afterRestore.status)
                    assertTrue(session.isFinished)
                    assertTrue(invalidated.diagnostics.any { it.code == "usvm.session.source.changed" })
                    assertEquals(first.edgeGuidance?.selections, invalidated.edgeGuidance?.selections)
                }
            }
        }
    }

    @Test
    fun `closing a paused session releases it and forbids later work`() {
        val initial = initialCoverage()
        val session = openSession(initial)
        session.advance(maxSteps = 1uL)

        session.close()
        session.close()

        assertTrue(session.isFinished)
        assertFailsWith<IllegalStateException> { session.advance() }
        assertFailsWith<IllegalStateException> { session.applyCoverage(initial) }
    }

    private fun resumeWithNativeEvidence(value: Int): ResumeObservation {
        val initial = initialCoverage()
        openSession(initial).use { session ->
            val first = pauseWithThreeStates(session)
            val trace = assertNotNull(first.edgeGuidance)
            assertEquals(2, trace.initialGoals.size, trace.toString())
            val beforeIds = trace.selections.map { it.stateId }.toSet()
            val example = listOf(JsConcreteValue.number(value.toDouble()))
            val later = assertNotNull(
                backend.run(
                    property = property,
                    configuration = configuration.copy(examples = listOf(example)),
                ).coverage,
            )

            val update = session.applyCoverage(later)
            val second = session.advance(maxSteps = 2uL)

            assertEquals(UsvmPropertySearchStatus.IN_PROGRESS, first.status)
            assertEquals(UsvmPropertySearchStatus.IN_PROGRESS, second.status)
            assertEquals(UsvmCoverageUpdateStatus.ACCEPTED, update.status)
            assertEquals(1, update.additions.size)
            val expectedGoal = (trace.initialGoals - update.additions).single()
            val secondTrace = assertNotNull(second.edgeGuidance)
            assertEquals(trace.selections, secondTrace.selections.take(trace.selections.size))
            val selected = secondTrace.selections.drop(trace.selections.size).first { !it.originalObjectiveTurn }
            assertEquals(expectedGoal, selected.goal)
            assertTrue(selected.stateId in beforeIds)
            val completed = session.advance()
            val repeated = session.advance()
            assertEquals(UsvmPropertySearchStatus.NO_VIOLATION_REACHED, completed.status)
            assertTrue(session.isFinished)
            assertEquals(completed.edgeGuidance?.selections, repeated.edgeGuidance?.selections)
            assertEquals(property.id, completed.propertyId)
            assertEquals(null, completed.inputs)
            return ResumeObservation(first = trace, selectedGoal = expectedGoal)
        }
    }

    private fun pauseWithThreeStates(session: UsvmPropertySearchSession): UsvmPropertySearchResult {
        var result = session.advance(maxSteps = 0uL)
        repeat(200) {
            result = session.advance(maxSteps = 1uL)
            val trace = assertNotNull(result.edgeGuidance)
            val seenThreeStates = trace.selections.map { selection -> selection.stateId }.distinct().size >= 3
            val threeStatesPending = trace.selections.last().pendingStates >= 3
            if (seenThreeStates && threeStatesPending) return result

            assertFalse(session.isFinished, trace.toString())
        }
        error("Three pending states were not observed: $result")
    }

    private fun initialCoverage(): PropertyCoverageArtifact = assertNotNull(
        backend.run(property = property, configuration = configuration).coverage,
    )

    private fun openSession(
        coverage: PropertyCoverageArtifact,
        scene: EtsScene = Companion.scene,
        roots: List<Path> = Companion.roots,
        definition: PropertyDefinition = property,
        stopOnTargetsReached: Boolean = false,
    ): UsvmPropertySearchSession {
        val manifest = definition.toManifest()
        val mapping = PropertyEtsMapper(scene = scene, sourceRoots = roots).map(manifest, coverage)
        val searcher = UsvmPropertySearcher(
            scene = scene,
            machineOptions = UMachineOptions(
                solverType = SolverType.YICES,
                timeout = 20.seconds,
                stopOnTargetsReached = stopOnTargetsReached,
            ),
        )
        return searcher.openSession(
            manifest = manifest,
            mapping = mapping,
            concreteCapability = ProjectionCapability(level = ProjectionLevel.EXACT),
            coverageFeedback = UsvmCoverageFeedback(initialCoverage = coverage) { null },
        )
    }

    private fun withCopiedSources(action: (Path) -> Unit) {
        val directory = Files.createTempDirectory("usvm-retained-property-")
        try {
            Files.copy(roots.single().resolve(MODULE), directory.resolve(MODULE))
            Files.copy(roots.single().resolve("retained-branches.ts"), directory.resolve("retained-branches.ts"))
            action(directory)
        } finally {
            Files.walk(directory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }

    private data class ResumeObservation(val first: UsvmEdgeGuidanceTrace, val selectedGoal: UsvmCoverageEdge)

    private companion object {
        const val MODULE = "retained-property.ts"
        val roots = listOf(testResourcesRoot().resolve("properties/retained-session"))
        val scene: EtsScene by lazy { loadEtsProjectAutoConvert(roots.single(), provider = EtsIrProvider.TS_FRONTEND) }
        val backend = FastCheckBackend(sourceRoots = roots)
        val property = PropertyDefinition(
            id = PropertyId("retained.original"),
            inputs = listOf(PropertyInput(name = "value", domain = IntegerDomain(min = -100, max = 100))),
            predicate = TypeScriptEntryPoint(module = MODULE, exportName = "valid"),
        )
        val configuration = PropertyRunConfiguration(
            seed = 42,
            numRuns = 1,
            timeoutMillis = 10_000,
            examples = listOf(listOf(JsConcreteValue.number(0.0))),
            coverageRequest = PropertyCoverageRequest(admittedOnly = true),
        )
    }
}
