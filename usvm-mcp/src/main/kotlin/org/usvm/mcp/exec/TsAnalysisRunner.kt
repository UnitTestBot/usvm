package org.usvm.mcp.exec

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStmt
import org.usvm.api.TsParametersState
import org.usvm.api.TsTest
import org.usvm.api.checkers.UncoveredIfSuccessors
import org.usvm.api.checkers.UnreachableCodeDetector
import org.usvm.api.targets.ReachabilityObserver
import org.usvm.api.targets.TsReachabilityTarget
import org.usvm.machine.TsAnalysisResult
import org.usvm.machine.TsAnalysisStopReason
import org.usvm.machine.TsInterpreterObserver
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.call.TsUnknownCallDecision
import org.usvm.machine.call.TsUnknownCallEvent
import org.usvm.machine.state.TsState
import org.usvm.statistics.CompositeUMachineObserver
import org.usvm.statistics.UMachineObserver
import org.usvm.util.TsTestResolver
import kotlin.time.Duration

private val logger = KotlinLogging.logger {}

/** A collected state resolved to a complete test, entry inputs, or a witness error. */
data class ResolvedCase(
    val test: TsTest?,
    val error: String?,
    val inputs: TsParametersState? = null,
)

/** Results and diagnostics from one bounded symbolic analysis. */
data class AnalysisOutcome<T>(
    val result: T,
    val stopReason: TsAnalysisStopReason,
    val unsupportedPaths: List<String>,
    val executionErrors: List<String>,
)

/** Reaching the target does not require reaching a method return. */
data class ReachabilityOutcome(
    val reached: Boolean,
    val witness: ResolvedCase?,
)

/**
 * Runs USVM analyses over TypeScript methods.
 *
 * All runs are serialized through a [Mutex]: the machine and the SMT solver
 * are heavyweight, and MCP clients may issue concurrent calls.
 */
class TsAnalysisRunner {

    private val mutex = Mutex()

    /** Explores the method and resolves every collected state to concrete inputs/outcome. */
    suspend fun runTests(
        scene: EtsScene,
        method: EtsMethod,
        timeout: Duration,
    ): AnalysisOutcome<List<ResolvedCase>> =
        withMachineLock {
            val options = AnalysisOptions.coverage(timeout)
            val diagnostics = AnalysisObserver()
            TsMachine(scene, options, TsOptions(), diagnostics, diagnostics).use { machine ->
                val analysis = machine.analyzeWithOutcome(listOf(method))
                analysis.withResult(analysis.states.map { resolveState(method, it) }, diagnostics)
            }
        }

    /** Directed search for a path reaching [target] inside [method]. */
    suspend fun runReachability(
        scene: EtsScene,
        method: EtsMethod,
        target: EtsStmt,
        timeout: Duration,
    ): AnalysisOutcome<ReachabilityOutcome> = withMachineLock {
        val options = AnalysisOptions.targeted(timeout)
        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        initialTarget.addChild(TsReachabilityTarget.FinalPoint(target))

        val diagnostics = AnalysisObserver()
        var reachedWitness: ResolvedCase? = null
        val witnessObserver = object : UMachineObserver<TsState> {
            override fun onStatePeeked(state: TsState) {
                if (reachedWitness == null && state.currentStatement == target) {
                    reachedWitness = resolveInputs(method, state)
                }
            }
        }
        val machineObserver = CompositeUMachineObserver(ReachabilityObserver(), witnessObserver, diagnostics)

        TsMachine(scene, options, TsOptions(), machineObserver, diagnostics).use { machine ->
            val analysis = machine.analyzeWithOutcome(listOf(method), listOf(initialTarget))
            val witnessState = analysis.states.firstOrNull { target in it.pathNode.allStatements }
            val reachability = ReachabilityOutcome(
                reached = reachedWitness != null || witnessState != null,
                witness = witnessState?.let { resolveState(method, it) } ?: reachedWitness,
            )

            analysis.withResult(reachability, diagnostics)
        }
    }

    /** Searches for `if` branches never taken during exploration of [methods]. */
    suspend fun runUnreachableDetection(
        scene: EtsScene,
        methods: List<EtsMethod>,
        timeout: Duration,
    ): AnalysisOutcome<Map<EtsMethod, List<UncoveredIfSuccessors>>> = withMachineLock {
        val options = AnalysisOptions.coverage(timeout)
        val detector = UnreachableCodeDetector()
        val diagnostics = AnalysisObserver()
        val interpreterObserver = object : TsInterpreterObserver by detector {
            override fun onUnknownCall(event: TsUnknownCallEvent) = diagnostics.onUnknownCall(event)
            override fun onStepFailure(state: TsState, error: Throwable) = diagnostics.onStepFailure(state, error)
        }
        val machineObserver = CompositeUMachineObserver(detector, diagnostics)
        val tsOptions = TsOptions(interproceduralAnalysis = false)
        TsMachine(scene, options, tsOptions, machineObserver, interpreterObserver).use { machine ->
            val analysis = machine.analyzeWithOutcome(methods)
            analysis.withResult(detector.result, diagnostics)
        }
    }

    private fun resolveState(method: EtsMethod, state: TsState): ResolvedCase =
        try {
            ResolvedCase(test = TsTestResolver().resolve(method, state), error = null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: InterruptedException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            logger.warn(e) { "Failed to resolve a symbolic state of ${method.name}" }
            ResolvedCase(test = null, error = "${e::class.simpleName}: ${e.message}")
        }

    private fun resolveInputs(method: EtsMethod, state: TsState): ResolvedCase =
        try {
            ResolvedCase(test = null, error = null, inputs = TsTestResolver().resolveInputs(method, state))
        } catch (e: CancellationException) {
            throw e
        } catch (e: InterruptedException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            logger.warn(e) { "Failed to resolve entry inputs of ${method.name}" }
            ResolvedCase(test = null, error = "${e::class.simpleName}: ${e.message}")
        }

    private suspend fun <T> withMachineLock(block: () -> T): T =
        mutex.withLock {
            runInterruptible(Dispatchers.IO) {
                block()
            }
        }
}

private class AnalysisObserver : TsInterpreterObserver, UMachineObserver<TsState> {
    val residualPaths = linkedSetOf<String>()
    val executionErrors = linkedSetOf<String>()

    override fun onUnknownCall(event: TsUnknownCallEvent) {
        if (event.decision is TsUnknownCallDecision.ResidualFallback) {
            residualPaths += "${event.callee}: ${event.failureReason} (${event.outcome}) at ${event.callSite}"
        }
    }

    override fun onStepFailure(state: TsState, error: Throwable) {
        executionErrors += "${error::class.simpleName}: ${error.message} at ${state.currentStatement}"
    }
}

private fun <T> TsAnalysisResult.withResult(result: T, diagnostics: AnalysisObserver): AnalysisOutcome<T> =
    AnalysisOutcome(
        result = result,
        stopReason = stopReason,
        unsupportedPaths = (unsupportedPaths + diagnostics.residualPaths).distinct(),
        executionErrors = diagnostics.executionErrors.toList(),
    )
