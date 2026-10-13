package org.usvm.ts.calls

import org.usvm.ts.pbt.fastcheck.TypeScriptClassStatementDescriptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CallsClassExperimentTest {
    @Test
    fun `class treatments reject sixty seconds and duplicate paired seeds`() {
        val manifest = manifest()

        assertEquals(listOf(30_000L, 120_000L), manifest.budgetsMillis)
        assertFailsWith<IllegalArgumentException> { manifest.copy(budgetsMillis = listOf(30_000L, 60_000L, 120_000L)) }
        assertFailsWith<IllegalArgumentException> { manifest.copy(seeds = listOf(17, 17)) }
    }

    @Test
    fun `class coverage unions only exact accepted replays emitted inside the budget`() {
        val hash = "a".repeat(64)
        val ids = (0..2).map { "$hash:$it" }
        val universe = CallsClassSourceUniverse(
            sourceFile = "Counter.ts",
            className = "Counter",
            sourceSha256 = hash,
            universeSha256 = "b".repeat(64),
            instrumenterVersion = "test",
            statements = ids.mapIndexed { index, id -> statement(id, index) },
        )
        val candidates = listOf(
            candidate(emittedAtMillis = 20, scenarioId = "first"),
            candidate(emittedAtMillis = 30, scenarioId = "second"),
            candidate(emittedAtMillis = 40, scenarioId = "second"),
            candidate(emittedAtMillis = 30_001, scenarioId = "late"),
        )
        val events = listOf(
            replay(index = 0, scenarioId = "first", covered = setOf(ids[0]), accepted = true),
            replay(index = 1, scenarioId = "second", covered = setOf(ids[0], ids[1]), accepted = true),
            replay(index = 2, scenarioId = "second", covered = setOf(ids[2]), accepted = false),
            replay(index = 3, scenarioId = "late", covered = setOf(ids[2]), accepted = true),
        )

        val checkpoint = callsClassCoverageCheckpoints(
            search = search(candidates),
            events = events,
            universe = universe,
            budgetMillis = 30_000,
        ).last()

        assertEquals(setOf(ids[0], ids[1]), checkpoint.coveredStatementIds)
        assertEquals(setOf(ids[2]), checkpoint.uncoveredStatementIds)
        assertEquals(3, checkpoint.totalStatements)
        assertEquals(2, checkpoint.acceptedCandidates)
        assertEquals(1, checkpoint.rejectedCandidates)
        assertTrue(checkpoint.coveredStatementIds.size < checkpoint.totalStatements)
    }

    @Test
    fun `class cell refuses a truncated replay journal`() {
        val hash = "a".repeat(64)
        val universe = CallsClassSourceUniverse(
            sourceFile = "Counter.ts",
            className = "Counter",
            sourceSha256 = hash,
            universeSha256 = hash,
            instrumenterVersion = "test",
            statements = listOf(statement("$hash:0", index = 0)),
        )

        assertFailsWith<IllegalArgumentException> {
            callsClassCoverageCheckpoints(
                search = search(listOf(candidate(emittedAtMillis = 1, scenarioId = "constructor"))),
                events = emptyList(),
                universe = universe,
                budgetMillis = 30_000,
            )
        }
    }

    private fun manifest(): CallsClassExperimentManifest = CallsClassExperimentManifest(
        experimentId = "class-pilot",
        toolRevision = "a".repeat(40),
        nativeFrontendRevision = "bundled:test",
        modelSet = CallsModelSetIdentity(ids = emptySet(), toolRevision = "a".repeat(40)),
        seeds = listOf(17),
        classes = listOf(
            CallsClassCase(
                classId = "counter",
                projectId = "fixture",
                revision = "c".repeat(40),
                sourceRoot = "fixture",
                sourceSha256 = mapOf("Counter.ts" to "a".repeat(64)),
                surface = CallsClassSurface(
                    sourceFile = "Counter.ts",
                    exportName = "Counter",
                    constructorInputs = emptyList(),
                    members = emptyList(),
                    stateFields = emptyMap(),
                ),
            ),
        ),
    )

    private fun candidate(emittedAtMillis: Long, scenarioId: String): CallsCoverageCandidate = CallsCoverageCandidate(
        emittedAtMillis = emittedAtMillis,
        emittedAtStep = 1,
        inputs = emptyList(),
        newSymbolicStatements = 1,
        completion = CallsCoverageCompletion.RETURNED,
        entryPointId = scenarioId,
    )

    private fun replay(
        index: Int,
        scenarioId: String,
        covered: Set<String>,
        accepted: Boolean,
    ): CallsClassReplayEvent = CallsClassReplayEvent(
        index = index,
        scenarioId = scenarioId,
        instrumented = CallsClassReplayObservation(
            inputs = emptyList(),
            observation = CallsClassObservation(completion = CallsCoverageCompletion.RETURNED),
            coveredStatementKeys = covered,
        ),
        oracle = CallsClassOracleResult(accepted = accepted),
    )

    private fun search(candidates: List<CallsCoverageCandidate>): CallsClassSearchResult = CallsClassSearchResult(
        search = CallsCoverageSearchResult(
            status = CallsCoverageSearchStatus.EXHAUSTED,
            candidates = candidates,
            selectedStates = candidates.size,
            executedSteps = candidates.size.toLong(),
            stepsWithinBudget = candidates.size.toLong(),
            extractionFailures = emptyList(),
            candidateCapReached = false,
            preparationElapsedMillis = 0,
            machineSetupElapsedMillis = 0,
            searchElapsedMillis = 0,
            machineTeardownElapsedMillis = 0,
        ),
        scenarios = emptyList(),
        visits = emptyList(),
        unknownCallEvents = 0,
    )

    private fun statement(id: String, index: Int) = TypeScriptClassStatementDescriptor(
        id = id,
        memberName = "increment",
        kind = "ExpressionStatement",
        startOffset = index,
        endOffset = index + 1,
        startLine = 1,
        startColumn = index,
        endLine = 1,
        endColumn = index + 1,
    )
}
