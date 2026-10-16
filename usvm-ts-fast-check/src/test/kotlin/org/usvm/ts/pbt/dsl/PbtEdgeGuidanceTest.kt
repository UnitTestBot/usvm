package org.usvm.ts.pbt.dsl

import org.junit.jupiter.api.Test
import org.usvm.ts.pbt.backend.PropertyRunConfiguration
import org.usvm.ts.pbt.model.JsConcreteValue
import org.usvm.ts.pbt.testResourcesRoot
import org.usvm.ts.pbt.usvm.UsvmEdgeReplayStatus
import org.usvm.ts.pbt.usvm.UsvmPropertySearchStatus
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PbtEdgeGuidanceTest {
    private val runner = DslPropertyRunner(sourceRoots = listOf(testResourcesRoot().resolve("properties/dsl")))
    private val configuration = PropertyRunConfiguration(seed = 42, numRuns = 1, timeoutMillis = 30_000)

    @Test
    fun `real PBT evidence changes the pending state selected in the original property search`() {
        val program = positiveResult(exportName = "branch")

        val positive = run(program, examples = listOf(1))
        val negative = run(program, examples = listOf(-1))

        val positiveTrace = assertNotNull(positive.symbolic?.edgeGuidance)
        val negativeTrace = assertNotNull(negative.symbolic?.edgeGuidance)
        assertEquals(setOf(true), positiveTrace.initialCovered.map { it.outcome }.toSet())
        assertEquals(setOf(false), positiveTrace.initialGoals.map { it.outcome }.toSet())
        assertEquals(setOf(false), negativeTrace.initialCovered.map { it.outcome }.toSet())
        assertEquals(setOf(true), negativeTrace.initialGoals.map { it.outcome }.toSet())
        val positiveSelection = positiveTrace.selections.first { it.pendingStates > 1 && it.distance == 0 }
        val negativeSelection = negativeTrace.selections.first { it.pendingStates > 1 && it.distance == 0 }
        assertEquals(false, positiveSelection.goal?.outcome)
        assertEquals(true, negativeSelection.goal?.outcome)
        assertTrue(positiveSelection.goal in positiveSelection.traversedGoals)
        assertTrue(negativeSelection.goal in negativeSelection.traversedGoals)
        assertEquals(setOf(false), positiveSelection.traversedGoals.map { it.outcome }.toSet())
        assertEquals(setOf(true), negativeSelection.traversedGoals.map { it.outcome }.toSet())
        val replayConfirmedNewEdge = positiveTrace.replays.any {
            it.status == UsvmEdgeReplayStatus.CONFIRMED && it.additions.isNotEmpty()
        }
        assertTrue(replayConfirmedNewEdge, positiveTrace.toString())
        assertTrue(positiveTrace.remainingGoals.isEmpty(), positiveTrace.toString())
        assertTrue(negativeTrace.remainingGoals.isEmpty(), negativeTrace.toString())
        assertEquals(UsvmPropertySearchStatus.NO_VIOLATION_REACHED, positive.symbolic?.status)
        assertEquals(UsvmPropertySearchStatus.NO_VIOLATION_REACHED, negative.symbolic?.status)
    }

    @Test
    fun `covered prefixes remain traversable for a nested missing edge and confirmed additions change later choices`() {
        val program = positiveResult(exportName = "nested")

        val run = run(program, examples = listOf(1))

        val trace = assertNotNull(run.symbolic?.edgeGuidance)
        val nestedMissing = trace.initialGoals.single { it.outcome }
        assertTrue(trace.replays.any { nestedMissing in it.additions }, trace.toString())
        assertTrue(trace.selections.any { it.evidenceRevision > 0 }, trace.toString())
        assertTrue(trace.remainingGoals.isEmpty(), trace.toString())
        assertEquals(UsvmPropertySearchStatus.NO_VIOLATION_REACHED, run.symbolic?.status)
    }

    @Test
    fun `native replay can make a previously unknown nested branch an exact goal in the same machine`() {
        val program = positiveResult(exportName = "hidden")

        val run = run(program, examples = listOf(-1))

        val trace = assertNotNull(run.symbolic?.edgeGuidance)
        assertTrue(trace.initialGoals.none { it.method.contains("nested") })
        assertTrue(trace.replays.any { replay -> replay.additions.any { it.method.contains("nested") } })
        val nestedOutcomes = trace.confirmedCovered.filter { it.method.contains("nested") }
            .groupBy { it.conditionIndex }.mapValues { (_, edges) -> edges.map { it.outcome }.toSet() }
        assertEquals(2, nestedOutcomes.size)
        val innerCondition = nestedOutcomes.entries.single { it.value == setOf(true, false) }.key
        val selectedInnerGoal = trace.selections.any {
            it.evidenceRevision > 0 && it.goal?.method?.contains("nested") == true &&
                it.goal?.conditionIndex == innerCondition
        }
        assertTrue(selectedInnerGoal)
        // hidden calls nested only for positive inputs: nested's outer false edge remains infeasible here.
        val remaining = trace.remainingGoals.single()
        assertTrue(remaining.method.contains("nested"))
        assertEquals(false, remaining.outcome)
        assertEquals(setOf(true), nestedOutcomes.getValue(remaining.conditionIndex))
        assertTrue(remaining !in trace.confirmedCovered)
        assertEquals(UsvmPropertySearchStatus.NO_VIOLATION_REACHED, run.symbolic?.status)
    }

    @Test
    fun `if without else retains a missing edge even when every statement was covered`() {
        val program = positiveResult(exportName = "withoutElse")

        val run = run(program, examples = listOf(1))

        val trace = assertNotNull(run.symbolic?.edgeGuidance)
        val coverage = assertNotNull(run.concrete?.coverage)
        val source = coverage.files.single { it.path.endsWith("edge-guidance.ts") }
        val functionStatements = source.statements.filter { it.location.start.line in 23..28 }
        assertTrue(functionStatements.isNotEmpty())
        assertTrue(functionStatements.all { it.hits > 0 }, functionStatements.toString())
        val missingOutcomes = trace.initialGoals.filter { it.method.contains("withoutElse") }.map { it.outcome }.toSet()
        assertEquals(setOf(false), missingOutcomes)
        assertTrue(trace.remainingGoals.isEmpty(), trace.toString())
    }

    @Test
    fun `fully covered code still searches for a violation of the original property`() {
        val program = defineProperty(id = "edge-guidance.saturated") {
            val branch = function<(Double) -> Double>(module = "edge-guidance.ts", exportName = "branch")
            val input by int(min = -100, max = 100)

            check(name = "original") { branch(input) notEqualTo input }
        }

        val run = run(program, examples = listOf(-1, 3))

        val trace = assertNotNull(run.symbolic?.edgeGuidance)
        assertTrue(trace.initialGoals.isEmpty(), trace.toString())
        assertTrue(trace.selections.isNotEmpty())
        assertTrue(trace.selections.all { it.goal == null && it.originalObjectiveTurn })
        assertEquals(UsvmPropertySearchStatus.VIOLATION_REACHED, run.symbolic?.status)
        assertEquals("original", run.symbolicReplay?.failure?.checkId)
    }

    private fun positiveResult(exportName: String): DslProperty = defineProperty(id = "edge-guidance.$exportName") {
        val function = function<(Double) -> Double>(module = "edge-guidance.ts", exportName = exportName)
        val input by int(min = -100, max = 100)

        check(name = "positive") { function(input) greaterThan 0 }
    }

    private fun run(program: DslProperty, examples: List<Int>): DslPropertyRun = runner.run(
        program = program,
        engine = PropertyEngine.BOTH,
        configuration = configuration.copy(
            numRuns = examples.size,
            examples = examples.map { listOf(JsConcreteValue.number(it.toDouble())) },
        ),
    )
}
