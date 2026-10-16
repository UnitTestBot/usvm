package org.usvm.ts.pbt.usvm

import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStmt
import org.usvm.UPathSelector
import org.usvm.machine.TsConcreteMethodCallStmt
import org.usvm.machine.TsEntryPointGuardResultStmt
import org.usvm.machine.TsGraph
import org.usvm.machine.TsMethodCall
import org.usvm.machine.state.TsState
import org.usvm.ps.BfsPathSelector
import org.usvm.ps.createClosestToUncoveredRandomPathSelector
import org.usvm.ts.pbt.backend.PropertyCoverageArtifact
import org.usvm.ts.pbt.backend.PropertyFailureKind
import org.usvm.ts.pbt.backend.PropertyRunResult
import org.usvm.ts.pbt.backend.PropertyRunStatus
import org.usvm.ts.pbt.coverage.captureCoverageIdentity
import org.usvm.ts.pbt.manifest.PropertyManifest
import org.usvm.ts.pbt.mapping.EtsBranchArmTarget
import org.usvm.ts.pbt.mapping.EtsMappingStatus
import org.usvm.ts.pbt.mapping.PropertyEtsMapper
import org.usvm.ts.pbt.mapping.PropertyEtsMappingArtifact
import org.usvm.ts.pbt.model.JsConcreteValue
import java.nio.file.Path
import java.util.concurrent.CancellationException
import kotlin.random.Random
import kotlin.time.TimeSource

/** Owns only exact concrete edge evidence and a seeded NURS scheduling hint, never path constraints. */
internal class UsvmEdgeGuidance(
    private val scene: EtsScene,
    private val manifest: PropertyManifest,
    mapping: PropertyEtsMappingArtifact,
    private val feedback: UsvmCoverageFeedback,
    val selectionSupported: Boolean,
    private val random: Random,
) {
    private val roots = mapping.provenance.sourceRoots.map(Path::of)
    private val started = TimeSource.Monotonic.markNow()
    private val identity = captureCoverageIdentity(manifest, roots)
    private val targets = linkedMapOf<UsvmCoverageEdge, EtsBranchArmTarget>()
    private val covered = linkedSetOf<UsvmCoverageEdge>()
    private val attempted = linkedSetOf<UsvmCoverageEdge>()
    private val attemptsByGoal = hashMapOf<UsvmCoverageEdge, Int>()
    private val replayedInputs = hashSetOf<List<JsConcreteValue>>()
    private val selections = mutableListOf<UsvmEdgeSelection>()
    private val replays = mutableListOf<UsvmEdgeReplay>()
    private val diagnostics = mutableListOf<String>()
    private var revision = 0
    private var omittedSelections = 0
    private val evidenceStatus: UsvmCoverageEvidenceStatus
    private val initialCovered: Set<UsvmCoverageEdge>
    private val initialGoals: Set<UsvmCoverageEdge>

    init {
        val coverage = feedback.initialCoverage
        evidenceStatus = evidenceStatus(coverage)
        val usable = evidenceStatus == UsvmCoverageEvidenceStatus.EXACT ||
            evidenceStatus == UsvmCoverageEvidenceStatus.PARTIAL
        if (usable) {
            val evidence = exactEdges(requireNotNull(coverage))
            evidence.forEach { (edge, target, hits) ->
                targets[edge] = target
                if (hits > 0) covered += edge
            }
        }
        initialCovered = covered.toSet()
        initialGoals = pendingGoals()
    }

    private fun evidenceStatus(coverage: PropertyCoverageArtifact?): UsvmCoverageEvidenceStatus {
        if (!selectionSupported) {
            diagnostics += "Edge scheduling does not support state merging or selector loop bounds; " +
                "original selection retained"
            return UsvmCoverageEvidenceStatus.UNSUPPORTED
        }
        if (coverage == null) return UsvmCoverageEvidenceStatus.ABSENT
        if (!coverage.provenance.request.admittedOnly || coverage.identity == null) {
            return UsvmCoverageEvidenceStatus.UNSUPPORTED
        }
        val stale = coverage.identity != identity || coverage.propertyId.value != manifest.propertyId ||
            coverage.diagnostics.any { it.code == "coverage.source.stale" }
        if (stale) return UsvmCoverageEvidenceStatus.STALE
        if (coverage.admittedInvocations == null) return UsvmCoverageEvidenceStatus.UNSUPPORTED
        if (coverage.admittedInvocations == 0) return UsvmCoverageEvidenceStatus.NO_ADMITTED_INPUTS

        val mapped = mapper().map(manifest, coverage).coverage
        val exact = exactEdges(coverage)
        val mappingDiagnostics = mapped.diagnostics + mapped.branches.flatMap { branch ->
            branch.mapping.diagnostics + branch.arms.flatMap { it.mapping.diagnostics }
        }
        diagnostics += mappingDiagnostics.map { "${it.code}: ${it.message}" }.distinct()
        if (exact.isEmpty()) return UsvmCoverageEvidenceStatus.UNSUPPORTED

        val unknownBranches = mapped.branches.any { it.mapping.status != EtsMappingStatus.EXACT }
        return if (unknownBranches || mapped.diagnostics.isNotEmpty()) {
            UsvmCoverageEvidenceStatus.PARTIAL
        } else {
            UsvmCoverageEvidenceStatus.EXACT
        }
    }

    private fun mapper() = PropertyEtsMapper(scene = scene, sourceRoots = roots)

    private fun exactEdges(coverage: PropertyCoverageArtifact): List<ObservedEdge> {
        return mapper().map(manifest, coverage).coverage.branches.flatMap { branch ->
            val sourcePath = branch.location?.path
            if (sourcePath !in identity.sourceSha256) return@flatMap emptyList()

            branch.arms.flatMap { arm ->
                if (arm.mapping.status != EtsMappingStatus.EXACT) return@flatMap emptyList()

                arm.mapping.targets.mapNotNull { target ->
                    val method = target.condition.location.method
                    val successors = method.cfg.successors(target.condition)
                    if (successors.distinct().size != 2) return@mapNotNull null

                    val edge = UsvmCoverageEdge(
                        method = method.signature.toString(),
                        conditionIndex = method.cfg.stmts.indexOf(target.condition),
                        successorIndex = method.cfg.stmts.indexOf(target.successor),
                        outcome = target.outcome,
                    )
                    ObservedEdge(edge = edge, target = target, hits = arm.coverage.hits)
                }
            }
        }
    }

    fun selector(initialStates: Map<EtsMethod, TsState>, graph: TsGraph): UPathSelector<TsState> {
        return EdgePathSelector(initialStates.values, graph)
    }

    fun confirm(state: TsState, inputs: List<JsConcreteValue>, expectsViolation: Boolean) {
        val claimed = traversedGoals(state)
        if (claimed.isEmpty()) return

        if (!replayedInputs.add(inputs)) return
        attempted += claimed
        claimed.forEach { goal -> attemptsByGoal[goal] = (attemptsByGoal[goal] ?: 0) + 1 }

        val replayStarted = TimeSource.Monotonic.markNow()
        val decision = replay(inputs, claimed, expectsViolation)

        replays += UsvmEdgeReplay(
            inputs = inputs,
            status = decision.status,
            additions = decision.additions,
            remainingGoals = pendingGoals(),
            elapsedMillis = replayStarted.elapsedNow().inWholeMilliseconds,
        )
    }

    // Feedback is optional: an arbitrary backend/transport exception must not
    // abort the original symbolic property objective or manufacture coverage.
    @Suppress("TooGenericExceptionCaught")
    private fun replay(
        inputs: List<JsConcreteValue>,
        claimed: Set<UsvmCoverageEdge>,
        expectsViolation: Boolean,
    ): ReplayDecision = try {
        val result = feedback.replay(inputs)
        if (result == null) {
            ReplayDecision(status = UsvmEdgeReplayStatus.NOT_RUN)
        } else {
            confirmReplay(result, inputs, claimed, expectsViolation)
        }
    } catch (error: InterruptedException) {
        Thread.currentThread().interrupt()
        throw error
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        if (Thread.currentThread().isInterrupted) throw error

        diagnostics += "Concrete replay failed: ${error.message}"
        ReplayDecision(status = UsvmEdgeReplayStatus.REPLAY_FAILED)
    }

    private fun confirmReplay(
        result: PropertyRunResult,
        inputs: List<JsConcreteValue>,
        claimed: Set<UsvmCoverageEdge>,
        expectsViolation: Boolean,
    ): ReplayDecision {
        val coverage = result.coverage
        val compatible = coverage != null && isCompatible(coverage)
        val matchesOracle = matchesOracle(result, inputs, expectsViolation)
        if (!matchesOracle || !compatible) {
            return ReplayDecision(status = UsvmEdgeReplayStatus.REJECTED_OR_INCOMPATIBLE)
        }

        val observed = exactEdges(requireNotNull(coverage))
        val confirmed = observed.filter { it.hits > 0 }.mapTo(hashSetOf()) { it.edge }
        if (!confirmed.containsAll(claimed)) return ReplayDecision(status = UsvmEdgeReplayStatus.EDGE_MISMATCH)

        observed.forEach { targets.putIfAbsent(it.edge, it.target) }
        val additions = confirmed - covered
        covered += additions
        if (additions.isNotEmpty()) revision += 1
        return ReplayDecision(status = UsvmEdgeReplayStatus.CONFIRMED, additions = additions)
    }

    private fun matchesOracle(
        result: PropertyRunResult,
        inputs: List<JsConcreteValue>,
        expectsViolation: Boolean,
    ): Boolean {
        val propertyFailure = result.failure?.kind == PropertyFailureKind.PROPERTY
        val completed = result.status == PropertyRunStatus.SUCCESS ||
            (propertyFailure && result.counterexample == inputs)
        val exact = result.numSkips == 0 && result.numRuns == 1 && result.numShrinks == 0
        return exact && completed && propertyFailure == expectsViolation
    }

    private fun isCompatible(coverage: PropertyCoverageArtifact): Boolean {
        val original = feedback.initialCoverage ?: return false
        val sameBackend = coverage.backendId == original.backendId &&
            coverage.backendVersion == original.backendVersion && coverage.kind == original.kind
        val fresh = coverage.identity == identity && captureCoverageIdentity(manifest, roots) == identity &&
            coverage.diagnostics.none { it.code == "coverage.source.stale" }
        val sameRuntime = coverage.provenance == original.provenance && coverage.admittedInvocations == 1
        return sameBackend && fresh && sameRuntime
    }

    private fun pendingGoals(): Set<UsvmCoverageEdge> = targets.keys - covered

    // Failed attempts remain uncovered. Try at most two distinct concrete inputs
    // per edge; property exploration retains half of all scheduling turns.
    private fun availableGoals(): Set<UsvmCoverageEdge> = pendingGoals().filterTo(linkedSetOf()) {
        (attemptsByGoal[it] ?: 0) < MAX_REPLAY_ATTEMPTS_PER_GOAL
    }

    private fun traversedGoals(state: TsState): Set<UsvmCoverageEdge> {
        if (state.entryPointGuardActive) return emptySet()

        val path = state.pathNode.allStatements.takeWhile { it !is TsEntryPointGuardResultStmt }
        return availableGoals().filterTo(linkedSetOf()) { edge ->
            val target = targets.getValue(edge)
            path.zipWithNext().any { (successor, condition) ->
                condition == target.condition && successor == target.successor
            }
        }
    }

    fun snapshot() = UsvmEdgeGuidanceTrace(
        evidenceStatus = evidenceStatus,
        initialCovered = initialCovered,
        initialGoals = initialGoals,
        confirmedCovered = covered.toSet(),
        remainingGoals = pendingGoals(),
        attemptedGoals = attempted.toSet(),
        attemptLimitPerGoal = MAX_REPLAY_ATTEMPTS_PER_GOAL,
        selections = selections.toList(),
        omittedSelections = omittedSelections,
        replays = replays.toList(),
        diagnostics = diagnostics.toList(),
        elapsedMillis = started.elapsedNow().inWholeMilliseconds,
    )

    private data class ObservedEdge(val edge: UsvmCoverageEdge, val target: EtsBranchArmTarget, val hits: Long)
    private data class GoalDistance(val goal: UsvmCoverageEdge, val distance: Int)
    private data class ReplayDecision(
        val status: UsvmEdgeReplayStatus,
        val additions: Set<UsvmCoverageEdge> = emptySet(),
    )

    private inner class EdgePathSelector(
        initialStates: Collection<TsState>,
        private val graph: TsGraph,
    ) : UPathSelector<TsState> {
        private val states = hashSetOf<TsState>()
        private val original = BfsPathSelector<TsState>()
        private val coverage = createClosestToUncoveredRandomPathSelector<TsState>(
            distance = { state -> nearestGoal(state)?.distance?.toUInt() ?: UInt.MAX_VALUE },
            random = random,
        )
        private var turn = 0L

        init {
            add(initialStates)
        }

        override fun isEmpty(): Boolean = states.isEmpty()
        override fun add(states: Collection<TsState>) {
            this.states.addAll(states)
            original.add(states)
            coverage.add(states)
        }
        override fun update(state: TsState) = coverage.update(state)
        override fun remove(state: TsState) {
            states.remove(state)
            original.remove(state)
            coverage.remove(state)
        }

        override fun drain(): Sequence<TsState> = sequence {
            for (state in coverage.drain()) {
                states.remove(state)
                original.remove(state)
                yield(state)
            }
        }

        override fun peek(): TsState {
            val originalTurn = turn % 2L == 1L
            val guidedState = if (originalTurn) null else coverage.peek()
            val goal = guidedState?.let { nearestGoal(it) }
            val state = if (goal == null) original.peek() else requireNotNull(guidedState)
            original.remove(state)
            original.add(listOf(state))
            turn += 1

            if (selections.size < MAX_RECORDED_SELECTIONS) {
                selections += UsvmEdgeSelection(
                    evidenceRevision = revision,
                    stateId = state.id,
                    statement = state.currentStatement.toString(),
                    pendingStates = states.size,
                    goal = goal?.goal,
                    distance = goal?.distance,
                    originalObjectiveTurn = originalTurn || goal == null,
                    traversedGoals = traversedGoals(state),
                )
            } else {
                omittedSelections += 1
            }
            return state
        }

        private fun nearestGoal(state: TsState): GoalDistance? {
            val pending = availableGoals()
            val crossed = traversedGoals(state).firstOrNull { it in pending }
            if (crossed != null) return GoalDistance(goal = crossed, distance = 0)
            if (pending.isEmpty()) return null

            val queue = ArrayDeque<Pair<EtsStmt, Int>>()
            val visited = hashSetOf<EtsStmt>()
            val returnSites = hashMapOf<EtsMethod, MutableSet<EtsStmt>>()
            state.callStack.forEach { frame ->
                val returnSite = frame.returnSite
                if (returnSite != null) returnSites.getOrPut(frame.method) { hashSetOf() } += returnSite
            }
            queue += state.currentStatement to 0
            while (queue.isNotEmpty() && visited.size < MAX_DISTANCE_STATEMENTS) {
                val (statement, distance) = queue.removeFirst()
                if (!visited.add(statement)) continue
                if (statement is TsEntryPointGuardResultStmt) {
                    queue += statement.entryPoint to distance + 1
                    continue
                }

                val goal = pending.firstOrNull { targets.getValue(it).condition == statement }
                if (goal != null) return GoalDistance(goal = goal, distance = distance + 1)

                graph.successors(statement).forEach { queue += it to distance + 1 }
                val callees = when (statement) {
                    is TsConcreteMethodCallStmt -> sequenceOf(statement.callee)
                    is TsMethodCall -> graph.callees(statement.returnSite)
                    else -> graph.callees(statement)
                }
                callees.forEach { callee ->
                    returnSites.getOrPut(callee) { hashSetOf() }.addAll(graph.successors(statement))
                    graph.entryPoints(callee).forEach { queue += it to distance + 1 }
                }
                // Returns are an overapproximation for priority only, never a reachability claim.
                val method = graph.methodOf(statement)
                if (statement in graph.exitPoints(method)) {
                    returnSites[method].orEmpty().forEach { queue += it to distance + 1 }
                }
            }
            return null
        }
    }

    private companion object {
        const val MAX_RECORDED_SELECTIONS = 4096
        const val MAX_DISTANCE_STATEMENTS = 20_000
        const val MAX_REPLAY_ATTEMPTS_PER_GOAL = 2
    }
}
