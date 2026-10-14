package org.usvm.ts.calls

import org.usvm.ts.pbt.fastcheck.TypeScriptClassStatementDescriptor
import org.usvm.ts.pbt.model.JsConcreteValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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
    fun `native primary coverage survives strict symbolic rejection and excludes late terminal candidates`() {
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

        assertEquals(ids.toSet(), checkpoint.coveredStatementIds)
        assertEquals(emptySet(), checkpoint.uncoveredStatementIds)
        assertEquals(3, checkpoint.totalStatements)
        assertEquals(3, checkpoint.acceptedCandidates)
        assertEquals(0, checkpoint.rejectedCandidates)
        assertEquals(setOf(ids[0], ids[1]), checkpoint.strictCoveredStatementIds)
        assertEquals(2, checkpoint.strictAcceptedCandidates)
        assertEquals(1, checkpoint.strictRejectedCandidates)
        assertTrue(checkpoint.strictCoveredStatementIds.size < checkpoint.totalStatements)
    }

    @Test
    fun `reached prefix uses real search time and post stop extraction appears only in final checkpoint`() {
        val universe = universe()
        val id = universe.statementIds.single()
        val prefix = candidate(emittedAtMillis = 20, scenarioId = "prefix").copy(
            completion = null,
            origin = CallsCoverageCandidateOrigin.SUSPENDED,
            suspensionReason = "Unsupported native call",
        )
        val frontier = prefix.copy(entryPointId = "frontier", emittedAtMillis = 30_050, suspendedAtMachineStop = true)
        val lateTerminal = candidate(emittedAtMillis = 30_051, scenarioId = "late")
        val latePrefix = prefix.copy(entryPointId = "late-prefix", emittedAtMillis = 30_052)
        val candidates = listOf(prefix, frontier, lateTerminal, latePrefix)
        val events = candidates.mapIndexed { index, candidate ->
            replay(index, requireNotNull(candidate.entryPointId), setOf(id), accepted = false).copy(
                origin = candidate.origin,
                strictOracleApplicable = candidate.origin == CallsCoverageCandidateOrigin.TERMINATED,
            )
        }

        val checkpoints = callsClassCoverageCheckpoints(search(candidates), events, universe, budgetMillis = 30_000)

        assertEquals(30_050, candidates[1].emittedAtMillis)
        assertEquals(1, checkpoints.first().emittedCandidates)
        assertEquals(0, checkpoints.first().postStopSuspendedCandidates)
        assertFalse(checkpoints.first().includesSuspendedExtraction)
        assertEquals(2, checkpoints.last().emittedCandidates)
        assertEquals(2, checkpoints.last().acceptedCandidates)
        assertEquals(2, checkpoints.last().strictOracleNotApplicable)
        assertEquals(1, checkpoints.last().postStopSuspendedCandidates)
        assertTrue(checkpoints.last().includesSuspendedExtraction)
    }

    @Test
    fun `primary coverage rejects transport mismatch foreign hits and incomplete native observations`() {
        val universe = universe()
        val observation = CallsClassReplayObservation(
            inputs = emptyList(),
            observation = CallsClassObservation(completion = CallsCoverageCompletion.RETURNED),
            coveredStatementKeys = universe.statementIds,
        )
        val incomplete = observation.copy(observation = observation.observation.copy(representable = false))
        val mismatch = observation.copy(
            observation = observation.observation.copy(completion = CallsCoverageCompletion.THREW),
        )
        val foreign = observation.copy(coveredStatementKeys = setOf("foreign"))
        val changedInputs = observation.copy(inputs = listOf(JsConcreteValue.Boolean(value = true)))

        assertTrue(callsClassNativeCoverageAcceptance(observation, observation, universe).accepted)
        assertFalse(callsClassNativeCoverageAcceptance(observation, mismatch, universe).accepted)
        assertFalse(callsClassNativeCoverageAcceptance(observation, foreign, universe).accepted)
        assertFalse(callsClassNativeCoverageAcceptance(observation, changedInputs, universe).accepted)
        assertFalse(callsClassNativeCoverageAcceptance(incomplete, incomplete, universe).accepted)
        assertFalse(callsClassNativeCoverageAcceptance(observation, observation, universe, error = "timeout").accepted)
        assertFalse(callsClassNativeCoverageAcceptance(null, observation, universe).accepted)
    }

    @Test
    fun `suspended candidates reject invented terminal observations and old measurement schema`() {
        val terminal = candidate(emittedAtMillis = 1, scenarioId = "constructor")

        assertFailsWith<IllegalArgumentException> { terminal.copy(origin = CallsCoverageCandidateOrigin.SUSPENDED) }
        assertFailsWith<IllegalArgumentException> {
            terminal.copy(
                completion = null,
                origin = CallsCoverageCandidateOrigin.SUSPENDED,
                observation = CallsClassObservation(completion = CallsCoverageCompletion.RETURNED),
            )
        }
        assertFailsWith<IllegalArgumentException> { manifest().copy(schemaVersion = 1) }
    }

    @Test
    fun `resume recomputes native and strict decisions and refuses changed candidate origin`() {
        val universe = universe()
        val observation = CallsClassObservation(completion = CallsCoverageCompletion.RETURNED)
        val candidate = candidate(emittedAtMillis = 1, scenarioId = "constructor").copy(observation = observation)
        val event = replay(index = 0, scenarioId = "constructor", covered = universe.statementIds, accepted = true)

        validateCallsClassReplayEvent(candidate, index = 0, event = event, universe = universe)

        assertFailsWith<IllegalArgumentException> {
            validateCallsClassReplayEvent(
                candidate,
                index = 0,
                event = event.copy(nativeCoverage = CallsClassOracleResult(accepted = false)),
                universe = universe,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            validateCallsClassReplayEvent(
                candidate,
                index = 0,
                event = event.copy(oracle = CallsClassOracleResult(accepted = false)),
                universe = universe,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            validateCallsClassReplayEvent(
                candidate,
                index = 0,
                event = event.copy(origin = CallsCoverageCandidateOrigin.SUSPENDED),
                universe = universe,
            )
        }
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
        original = CallsClassReplayObservation(
            inputs = emptyList(),
            observation = CallsClassObservation(completion = CallsCoverageCompletion.RETURNED),
        ),
        instrumented = CallsClassReplayObservation(
            inputs = emptyList(),
            observation = CallsClassObservation(completion = CallsCoverageCompletion.RETURNED),
            coveredStatementKeys = covered,
        ),
        oracle = CallsClassOracleResult(accepted = accepted),
        nativeCoverage = CallsClassOracleResult(accepted = true),
    )

    private fun universe(): CallsClassSourceUniverse {
        val hash = "a".repeat(64)
        return CallsClassSourceUniverse(
            sourceFile = "Counter.ts",
            className = "Counter",
            sourceSha256 = hash,
            universeSha256 = hash,
            instrumenterVersion = "test",
            statements = listOf(statement("$hash:0", index = 0)),
        )
    }

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
