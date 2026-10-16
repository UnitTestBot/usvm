package org.usvm.ts.pbt.campaign

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.usvm.ts.pbt.backend.PropertyCoverageRequest
import org.usvm.ts.pbt.backend.PropertyRunConfiguration
import org.usvm.ts.pbt.backend.WitnessOracleOutcome
import org.usvm.ts.pbt.backend.WitnessReplayDeadline
import org.usvm.ts.pbt.backend.WitnessReplayStatus
import org.usvm.ts.pbt.fastcheck.FastCheckBackend
import org.usvm.ts.pbt.manifest.PropertyManifestJson
import org.usvm.ts.pbt.model.ExecutionKind
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.ts.pbt.model.encodeToUtf8SafeString
import org.usvm.ts.pbt.observation.PropertyObservationSource
import org.usvm.ts.pbt.testResourcesRoot
import org.usvm.ts.pbt.usvm.UsvmCompletedInput
import org.usvm.ts.pbt.usvm.UsvmPropertySearchStatus
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SymbolicInputReplayTest {
    @TempDir
    lateinit var directory: Path

    private val root = testResourcesRoot().resolve("properties/campaign")
    private val configuration = PropertyRunConfiguration(timeoutMillis = 60_000)

    @Test
    fun `healthy symbolic-only class paths produce checked native source coverage without a fault`() {
        val artifact = PropertyCampaignRunner(sourceRoot = root).runArtifact(
            property = property(exportName = "healthy"),
            mode = PropertyCampaignMode.SYMBOLIC_ONLY,
            configuration = configuration,
            completedInputLimit = 8,
        )
        val path = directory.resolve("healthy.json")

        artifact.write(path)
        val restored = PropertyCampaignArtifact.read(path)

        assertNull(restored.result.concrete)
        assertEquals(UsvmPropertySearchStatus.NO_VIOLATION_REACHED, restored.result.symbolic?.status)
        val replays = restored.result.symbolicInputReplays
        assertTrue(replays.size >= 2, restored.toString())
        assertTrue(replays.all { it.predictedOutcomes == setOf(WitnessOracleOutcome.HOLDS) })
        assertTrue(replays.all { it.replay.outcome == WitnessOracleOutcome.HOLDS })
        val checked = replays.flatMap { assertNotNull(it.admittedCoverage).files }
            .filter { it.path.endsWith("CompletedSubject.ts") }
        assertTrue(checked.isNotEmpty())
        assertTrue(checked.any { file -> file.statements.any { it.hits > 0 } })
        assertEquals(artifact.result.symbolicInputReplays, replays)
        assertFalse(restored.result.confirmedViolation)

        val first = replays.first()
        val other = first.copy(candidate = first.candidate.copy(propertyId = PropertyId(value = "other.property")))
        assertFailsWith<IllegalArgumentException> {
            artifact.copy(result = artifact.result.copy(symbolicInputReplays = listOf(other)))
        }
        val coverage = assertNotNull(first.replay.coverage).copy(propertyId = PropertyId(value = "other.property"))
        val wrongCoverage = first.copy(replay = first.replay.copy(coverage = coverage))
        assertFailsWith<IllegalArgumentException> {
            artifact.copy(result = artifact.result.copy(symbolicInputReplays = listOf(wrongCoverage)))
        }
    }

    @Test
    fun `fault inputs remain raw until the separate original confirmation and retain checked fault coverage`() {
        val artifact = PropertyCampaignRunner(sourceRoot = root).runArtifact(
            property = property(exportName = "faulty"),
            mode = PropertyCampaignMode.SYMBOLIC_ONLY,
            configuration = configuration,
            completedInputLimit = 8,
            shrinkWitness = false,
        )

        assertEquals(UsvmPropertySearchStatus.VIOLATION_REACHED, artifact.result.symbolic?.status)
        val rawFaults = artifact.result.symbolicInputReplays.filter { it.replay.outcome == WitnessOracleOutcome.FALSE }
        assertTrue(rawFaults.isNotEmpty(), artifact.toString())
        assertTrue(rawFaults.all { it.admittedCoverage == null })
        assertEquals(WitnessReplayStatus.CONFIRMED_PROPERTY_VIOLATION, artifact.result.confirmation?.status)
        val faultCoverage = assertNotNull(artifact.result.confirmation?.originalReplay?.coverage)
        assertTrue(faultCoverage.files.any { it.path.endsWith("CompletedSubject.ts") })
        assertTrue(artifact.result.confirmedViolation)
    }

    @Test
    fun `capture cap stops collection while the original healthy search continues`() {
        val artifact = PropertyCampaignRunner(sourceRoot = root).runArtifact(
            property = property(exportName = "healthy"),
            mode = PropertyCampaignMode.SYMBOLIC_ONLY,
            configuration = configuration,
            completedInputLimit = 1,
        )

        val symbolic = assertNotNull(artifact.result.symbolic)
        assertEquals(1, symbolic.completedInputs.size)
        assertTrue(symbolic.omittedCompletedInputs > 0, symbolic.toString())
        assertEquals(1, artifact.result.symbolicInputReplays.size)
        assertEquals(UsvmPropertySearchStatus.NO_VIOLATION_REACHED, symbolic.status)
    }

    @Test
    fun `rejected preconditions never become completed native inputs`() {
        val property = property(exportName = "healthy").copy(
            precondition = TypeScriptEntryPoint(module = MODULE, exportName = "positive"),
        )
        val artifact = PropertyCampaignRunner(sourceRoot = root).runArtifact(
            property = property,
            mode = PropertyCampaignMode.SYMBOLIC_ONLY,
            configuration = configuration,
            completedInputLimit = 8,
        )

        val inputs = assertNotNull(artifact.result.symbolic).completedInputs
        assertTrue(inputs.isNotEmpty())
        assertTrue(inputs.all { (it.inputs.single() as JsConcreteValue.Number).toDouble() > 0 })
        assertTrue(artifact.result.symbolicInputReplays.all { it.admittedCoverage != null })
    }

    @Test
    fun `unsupported symbolic capture stays explicit and does not manufacture native coverage`() {
        val property = property(exportName = "healthy").copy(
            predicate = TypeScriptEntryPoint(
                module = MODULE,
                exportName = "healthy",
                executionKind = ExecutionKind.ASYNC,
            ),
        )

        val artifact = PropertyCampaignRunner(sourceRoot = root).runArtifact(
            property = property,
            mode = PropertyCampaignMode.SYMBOLIC_ONLY,
            configuration = configuration,
            completedInputLimit = 8,
        )

        val symbolic = assertNotNull(artifact.result.symbolic)
        assertEquals(UsvmPropertySearchStatus.UNSUPPORTED, symbolic.status)
        assertTrue(symbolic.diagnostics.isNotEmpty())
        assertTrue(symbolic.completedInputs.isEmpty())
        assertTrue(artifact.result.symbolicInputReplays.isEmpty())
    }

    @Test
    fun `immutable inputs and dedup reuse the guidance native operation instead of executing twice`() {
        val log = directory.resolve("calls.txt")
        val logLiteral = PropertyManifestJson.json.encodeToUtf8SafeString(log.toString())
        writePredicate(
            """
            import { appendFileSync } from 'node:fs';
            export function holds(value: number): boolean {
                appendFileSync($logLiteral, String(value) + '\n');
                return value === value;
            }
            """.trimIndent(),
        )
        val collector = collector()
        val mutableInputs = mutableListOf<JsConcreteValue>(JsConcreteValue.number(1.0))

        collector.replayForGuidance(mutableInputs)
        mutableInputs[0] = JsConcreteValue.number(2.0)
        val original = completion(value = 1.0)
        collector.collect(listOf(original, original))

        assertEquals(listOf("1", "1"), Files.readAllLines(log))
        assertEquals(1, collector.replays.size)
        assertEquals(original.inputs, collector.replays.single().candidate.inputs)
        assertNotNull(collector.replays.single().admittedCoverage)
    }

    @Test
    fun `source drift rejects native evidence without replacing its original snapshot`() {
        writePredicate("export function holds(value: number): boolean { return value === value; }")
        val originalHash = campaignSourceSha256(directory.resolve("predicate.ts"))
        val collector = collector()
        Files.writeString(directory.resolve("predicate.ts"), "\n", java.nio.file.StandardOpenOption.APPEND)

        collector.collect(listOf(completion(value = 1.0)))

        val replay = collector.replays.single()
        assertNull(replay.admittedCoverage)
        assertTrue(replay.replay.diagnostic?.contains("source-hash") == true, replay.toString())
        assertTrue(replay.candidate.sources.any { it.sha256 == originalHash })
        assertNotNull(collector.diagnostic)
    }

    @Test
    fun `nondeterminism and a native oracle mismatch never contribute coverage`() {
        writePredicate("let calls = 0; export function holds(value: number): boolean { calls++; return calls === 1; }")
        val nondeterministic = collector()

        nondeterministic.collect(listOf(completion(value = 1.0)))

        val nativeReplay = nondeterministic.replays.single()
        assertTrue(nativeReplay.replay.diagnostic?.contains("nondeterministic") == true)
        assertNull(nativeReplay.admittedCoverage)

        writePredicate("export function holds(value: number): boolean { return false; }")
        val mismatch = collector()

        mismatch.collect(listOf(completion(value = 1.0)))

        assertEquals(WitnessOracleOutcome.FALSE, mismatch.replays.single().replay.outcome)
        assertEquals("symbolic.native.oracle-mismatch", mismatch.replays.single().replay.diagnostic)
        assertNull(mismatch.replays.single().admittedCoverage)
    }

    @Test
    fun `expired native budget preserves unchecked inputs and never credits a late receipt`() {
        writePredicate("export function holds(value: number): boolean { while (value === value) {} return true; }")
        val deadline = WitnessReplayDeadline(timeoutMillis = 500)
        val collector = collector(deadline = deadline)

        collector.collect(listOf(completion(value = 1.0), completion(value = 2.0)))

        assertTrue(collector.timedOut)
        assertNotNull(collector.diagnostic)
        assertTrue(collector.replays.all { it.admittedCoverage == null })
        assertTrue(collector.replays.size <= 1)
    }

    private fun collector(
        deadline: WitnessReplayDeadline = WitnessReplayDeadline(timeoutMillis = 30_000),
    ): SymbolicInputReplayCollector {
        val property = property(exportName = "holds").copy(
            predicate = TypeScriptEntryPoint(module = "predicate.ts", exportName = "holds"),
        )
        val sources = listOf(
            PropertyObservationSource(
                module = "predicate.ts",
                sha256 = campaignSourceSha256(directory.resolve("predicate.ts")),
            ),
        )
        return SymbolicInputReplayCollector(
            property = property,
            backend = FastCheckBackend(sourceRoots = listOf(directory)),
            root = directory.toRealPath(),
            sources = sources,
            coverageRequest = PropertyCoverageRequest(admittedOnly = true),
            deadline = deadline,
            validationReserveMillis = 0,
            limit = 8,
        )
    }

    private fun property(exportName: String) = PropertyDefinition(
        id = PropertyId("completed.$exportName"),
        inputs = listOf(PropertyInput(name = "value", domain = IntegerDomain(min = -100, max = 100))),
        predicate = TypeScriptEntryPoint(module = MODULE, exportName = exportName),
    )

    private fun completion(value: Double) = UsvmCompletedInput(
        inputs = listOf(JsConcreteValue.number(value)),
        expectedOutcome = WitnessOracleOutcome.HOLDS,
    )

    private fun writePredicate(source: String) = directory.resolve("predicate.ts").writeText(source)

    private companion object {
        const val MODULE = "CompletedProperties.ts"
    }
}
