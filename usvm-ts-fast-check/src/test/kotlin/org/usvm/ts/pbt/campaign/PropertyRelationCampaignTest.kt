package org.usvm.ts.pbt.campaign

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.usvm.ts.pbt.backend.PropertyRunConfiguration
import org.usvm.ts.pbt.backend.WitnessOracleOutcome
import org.usvm.ts.pbt.backend.WitnessReplayStatus
import org.usvm.ts.pbt.backend.WitnessRole
import org.usvm.ts.pbt.dsl.CompiledDslProperty
import org.usvm.ts.pbt.dsl.DslNumericLocalCapture
import org.usvm.ts.pbt.dsl.PropertyTypeScriptCompiler
import org.usvm.ts.pbt.dsl.defineProperty
import org.usvm.ts.pbt.dsl.invoke
import org.usvm.ts.pbt.dsl.lessOrEqual
import org.usvm.ts.pbt.inference.ScalarGuidanceExclusionReason
import org.usvm.ts.pbt.inference.ScalarGuidanceRelevance
import org.usvm.ts.pbt.inference.ScalarObservationPair
import org.usvm.ts.pbt.inference.ScalarRelationTemplate
import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.PropertyAssertion
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.PropertyOperand
import org.usvm.ts.pbt.model.PropertySourcePoint
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.ts.pbt.observation.ObservationBuildStatus
import org.usvm.ts.pbt.observation.ObservationPointKind
import org.usvm.ts.pbt.observation.PropertyObservationPoint
import org.usvm.ts.pbt.usvm.UsvmPropertySearchStatus
import org.usvm.ts.pbt.usvm.UsvmRelationRefutationStatus
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PropertyRelationCampaignTest {
    @TempDir
    lateinit var root: Path

    private val configuration = PropertyRunConfiguration(seed = 42, numRuns = 8, timeoutMillis = 30_000)

    @Test
    fun `one PBT snapshot challenges a sampled local equality and retains the unrestricted original search`() {
        val compiled = numericProperty()

        val artifact = run(compiled, PropertyRelationGuidance.numericLocal(compiled))
        val file = root.resolve("campaign.json")
        artifact.write(file)
        val restored = PropertyCampaignArtifact.read(file)

        val trace = assertNotNull(restored.result.relationGuidance)
        val selection = assertNotNull(trace.selection)
        val attempt = trace.attempts.single()
        assertEquals(8, restored.result.concrete?.numRuns)
        assertEquals(ObservationBuildStatus.UNVERIFIED, selection.inference.buildStatus)
        assertEquals(ScalarGuidanceRelevance.KNOWN_DSL_CHECK, attempt.selected.relevance)
        assertEquals(ScalarRelationTemplate.STRICT_EQUAL, attempt.selected.relation.template)
        assertTrue(selection.excluded.any { it.reason == ScalarGuidanceExclusionReason.ORIGINAL_CHECK })
        assertEquals(UsvmRelationRefutationStatus.REFUTATION_REACHED, attempt.searchStatus)
        assertEquals(WitnessRole.HYPOTHESIS, attempt.witness?.target?.role)
        assertEquals(WitnessReplayStatus.CONFIRMED_HYPOTHESIS_REFUTATION, attempt.confirmation?.status)
        assertEquals(WitnessOracleOutcome.HOLDS, attempt.confirmation?.originalReplay?.outcome)
        assertNotNull(attempt.confirmation?.originalReplay?.coverage?.identity)
        assertTrue(attempt.completedWithinDeadline)
        assertEquals(UsvmPropertySearchStatus.NO_VIOLATION_REACHED, restored.result.symbolic?.status)
        assertTrue(trace.originalAllocatedMillis >= trace.originalReserveMillis)
        assertFalse(restored.result.confirmedViolation)
        assertEquals(artifact.result.relationGuidance, trace)
    }

    @Test
    fun `an original named fault found by a relation query stays a property violation`() {
        val compiled = numericProperty(delta = "+ 1")
        val guidance = PropertyRelationGuidance.numericLocal(compiled)
        val pair = guidance.pairs.single()
        val twoCandidates = guidance.copy(pairs = guidance.pairs + ScalarObservationPair(pair.right, pair.left))

        val artifact = run(compiled, twoCandidates)

        val trace = assertNotNull(artifact.result.relationGuidance)
        val attempt = trace.attempts.single()
        val canonical = assertNotNull(artifact.result.confirmation)
        assertEquals(2, assertNotNull(trace.selection).candidates.size)
        assertEquals(WitnessReplayStatus.CONFIRMED_PROPERTY_VIOLATION, attempt.confirmation?.status)
        assertEquals("bound", attempt.confirmation?.originalReplay?.failure?.checkId)
        assertEquals(attempt.confirmation, canonical)
        assertEquals(WitnessReplayStatus.CONFIRMED_PROPERTY_VIOLATION, canonical.status)
        assertEquals("bound", canonical.originalReplay?.failure?.checkId)
        assertTrue(artifact.result.confirmedViolation)
        assertEquals(PropertyCampaignStop.COMPLETED, artifact.result.stop)
        assertNull(artifact.result.symbolic)
        assertEquals(0, trace.originalAllocatedMillis)
        assertEquals(0, trace.originalElapsedMillis)
    }

    @Test
    fun `a timed out auxiliary allocation leaves positive time for the original objective`() {
        val compiled = numericProperty()
        val guidance = PropertyRelationGuidance.numericLocal(compiled).copy(maxRelationMillis = 1)

        val artifact = run(compiled, guidance)

        val trace = assertNotNull(artifact.result.relationGuidance)
        assertTrue(trace.attempts.size <= 1)
        assertEquals(1, trace.allocatedMillis)
        assertTrue(trace.originalAllocatedMillis >= guidance.originalReserveMillis)
        assertEquals(UsvmPropertySearchStatus.NO_VIOLATION_REACHED, artifact.result.symbolic?.status)
        assertFalse(artifact.result.confirmedViolation)
    }

    @Test
    fun `unsupported prefixes retain their reason without removing the original objective`() {
        val compiled = numericProperty(sparsePrefix = true)

        val artifact = run(compiled, PropertyRelationGuidance.numericLocal(compiled))

        val trace = assertNotNull(artifact.result.relationGuidance)
        val attempt = trace.attempts.single()
        assertEquals(UsvmRelationRefutationStatus.UNSUPPORTED, attempt.searchStatus)
        assertTrue(attempt.diagnostics.any { it.code == "usvm.runtime.array-index-growth" })
        assertEquals(UsvmPropertySearchStatus.UNSUPPORTED, artifact.result.symbolic?.status)
        assertTrue(trace.originalAllocatedMillis > 0)
        assertFalse(artifact.result.confirmedViolation)
    }

    @Test
    fun `insufficient observations remain evidence and do not suppress the original search`() {
        val compiled = numericProperty()

        val artifact = run(compiled, PropertyRelationGuidance.numericLocal(compiled), numRuns = 1)

        val trace = assertNotNull(artifact.result.relationGuidance)
        assertTrue(trace.attempts.isEmpty())
        assertTrue(assertNotNull(trace.selection).excluded.isNotEmpty())
        assertEquals(UsvmPropertySearchStatus.NO_VIOLATION_REACHED, artifact.result.symbolic?.status)
    }

    @Test
    fun `unknown relevance is skipped by default and only explicit opt in activates its exact entry challenge`() {
        Files.writeString(
            root.resolve("entry.ts"),
            "export function holds(a: number, b: number): boolean { return a === a; }"
        )
        val call = PropertySourcePoint(module = "entry.ts", line = 1, column = 1)
        val left = PropertyObservationPoint(
            id = "left",
            assertionId = "original",
            operandId = "left",
            source = call,
            callSite = call,
            kind = ObservationPointKind.ENTRY_ARGUMENT,
            inputIndex = 0,
        )
        val right = left.copy(id = "right", operandId = "right", inputIndex = 1)
        val property = PropertyDefinition(
            id = PropertyId(value = "entry.guidance"),
            inputs = listOf(
                PropertyInput(name = "a", domain = IntegerDomain(min = 0, max = 2)),
                PropertyInput(name = "b", domain = IntegerDomain(min = 0, max = 2)),
            ),
            predicate = TypeScriptEntryPoint(module = "entry.ts", exportName = "holds"),
            assertions = listOf(
                PropertyAssertion(
                    id = "original",
                    source = call,
                    testedCall = call,
                    operands = listOf(
                        PropertyOperand(id = "left", source = call),
                        PropertyOperand(id = "right", source = call)
                    ),
                )
            ),
        )
        val guidance = PropertyRelationGuidance(pairs = listOf(ScalarObservationPair(left, right)))
        val runner = PropertyCampaignRunner(sourceRoot = root)
        val configuration = configuration.copy(seed = 1, numRuns = 2)

        val skipped = runner.runArtifact(
            property,
            mode = PropertyCampaignMode.SEQUENTIAL,
            configuration,
            pbtAllocationMillis = 8_000,
            shrinkWitness = false,
            relationGuidance = guidance,
        )
        val optedIn = runner.runArtifact(
            property,
            mode = PropertyCampaignMode.SEQUENTIAL,
            configuration,
            pbtAllocationMillis = 8_000,
            shrinkWitness = false,
            relationGuidance = guidance.copy(allowUnknownRelevance = true),
        )

        val skippedTrace = assertNotNull(skipped.result.relationGuidance)
        val selectedTrace = assertNotNull(optedIn.result.relationGuidance)
        assertTrue(skippedTrace.attempts.isEmpty())
        assertTrue(skippedTrace.skippedUnknownRelevance.isNotEmpty())
        assertTrue(selectedTrace.attempts.isNotEmpty())
        assertTrue(selectedTrace.attempts.all { it.selected.relevance == ScalarGuidanceRelevance.UNKNOWN })
        assertTrue(
            selectedTrace.attempts.any {
                it.confirmation?.status == WitnessReplayStatus.CONFIRMED_HYPOTHESIS_REFUTATION
            }
        )
        assertEquals(UsvmPropertySearchStatus.NO_VIOLATION_REACHED, optedIn.result.symbolic?.status)
    }

    private fun run(
        compiled: CompiledDslProperty,
        guidance: PropertyRelationGuidance,
        numRuns: Int = 8,
    ) = PropertyCampaignRunner(sourceRoot = root).runArtifact(
        property = compiled.definition,
        mode = PropertyCampaignMode.SEQUENTIAL,
        configuration = configuration.copy(numRuns = numRuns),
        pbtAllocationMillis = 8_000,
        shrinkWitness = false,
        relationGuidance = guidance,
    )

    private fun numericProperty(delta: String = "- 1", sparsePrefix: Boolean = false): CompiledDslProperty {
        val prefix = if (sparsePrefix) "const cache: number[] = []; cache[1001] = input;" else ""
        Files.writeString(
            root.resolve("functions.ts"),
            """
            export function transform(input: number): number {
                $prefix
                if (input === 417) return input $delta;
                return input;
            }
            """.trimIndent()
        )
        val program = defineProperty(id = "campaign.local-relation") {
            val transform = function<(Double) -> Double>(module = "functions.ts", exportName = "transform")
            val input by int(min = 0, max = 1000)
            val result by value(transform(input))

            check(name = "bound") { result lessOrEqual input }
        }
        val compiled = PropertyTypeScriptCompiler().compile(
            program,
            generatedModule = "generated.ts",
            resolveModule = { "./$it" },
            numericLocalCapture = DslNumericLocalCapture(localName = "result", checkName = "bound"),
        )
        Files.writeString(root.resolve("generated.ts"), compiled.typeScript)

        return compiled
    }
}
