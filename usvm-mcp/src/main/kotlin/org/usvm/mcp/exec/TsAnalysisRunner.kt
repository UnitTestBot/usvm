package org.usvm.mcp.exec

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStmt
import org.usvm.api.TsTest
import org.usvm.api.checkers.UncoveredIfSuccessors
import org.usvm.api.checkers.UnreachableCodeDetector
import org.usvm.api.targets.ReachabilityObserver
import org.usvm.api.targets.TsReachabilityTarget
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.machine.state.TsState
import org.usvm.util.TsTestResolver
import kotlin.time.Duration

private val logger = KotlinLogging.logger {}

/** A symbolic state either resolved to a concrete test or failed to resolve. */
data class ResolvedCase(
    val test: TsTest?,
    val error: String?,
)

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
    suspend fun runTests(scene: EtsScene, method: EtsMethod, timeout: Duration): List<ResolvedCase> =
        withMachineLock {
            val options = AnalysisOptions.coverage(timeout)
            TsMachine(scene, options, TsOptions()).use { machine ->
                val states = machine.analyze(listOf(method))
                states.map { resolveState(method, it) }
            }
        }

    /** Directed search for a path reaching [target] inside [method]. */
    suspend fun runReachability(
        scene: EtsScene,
        method: EtsMethod,
        target: EtsStmt,
        timeout: Duration,
    ): ReachabilityOutcome = withMachineLock {
        val options = AnalysisOptions.targeted(timeout)
        val initialTarget = TsReachabilityTarget.InitialPoint(method.cfg.stmts.first())
        initialTarget.addChild(TsReachabilityTarget.FinalPoint(target))

        TsMachine(scene, options, TsOptions(), machineObserver = ReachabilityObserver()).use { machine ->
            val states = machine.analyze(listOf(method), listOf(initialTarget))
            val witnessState = states.firstOrNull { target in it.pathNode.allStatements }
            ReachabilityOutcome(
                reached = witnessState != null,
                witness = witnessState?.let { resolveState(method, it) },
            )
        }
    }

    /** Searches for `if` branches never taken during exploration of [methods]. */
    suspend fun runUnreachableDetection(
        scene: EtsScene,
        methods: List<EtsMethod>,
        timeout: Duration,
    ): Map<EtsMethod, List<UncoveredIfSuccessors>> = withMachineLock {
        val options = AnalysisOptions.coverage(timeout)
        val detector = UnreachableCodeDetector()
        // The detector observes both the machine and the interpreter, so it is passed twice.
        val tsOptions = TsOptions(interproceduralAnalysis = false)
        TsMachine(scene, options, tsOptions, detector, detector).use { machine ->
            machine.analyze(methods)
        }
        detector.result
    }

    private fun resolveState(method: EtsMethod, state: TsState): ResolvedCase =
        try {
            ResolvedCase(test = TsTestResolver().resolve(method, state), error = null)
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            logger.warn(e) { "Failed to resolve a symbolic state of ${method.name}" }
            ResolvedCase(test = null, error = "${e::class.simpleName}: ${e.message}")
        }

    private suspend fun <T> withMachineLock(block: () -> T): T =
        mutex.withLock {
            runInterruptible(Dispatchers.IO) {
                block()
            }
        }
}
