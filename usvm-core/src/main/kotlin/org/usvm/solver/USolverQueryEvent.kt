package org.usvm.solver

import io.ksmt.expr.KExpr
import io.ksmt.solver.KSolver
import io.ksmt.solver.KSolverConfiguration
import io.ksmt.solver.KSolverStatus
import io.ksmt.sort.KBoolSort
import mu.KLogging
import kotlin.time.Duration
import kotlin.time.TimeSource

private val logger = object : KLogging() {}.logger

/**
 * One completed KSolver check or checkWithAssumptions call, including a failed call.
 *
 * Measures wall time inside the check, excluding assertions, model decoding and type solving.
 * This is not the total cost of a USolver query, which may perform several SMT checks.
 */
data class USolverQueryEvent(
    val elapsedNanos: Long,
    val status: KSolverStatus?,
    val failure: String?,
)

/** Observer failures are logged without changing the solver result or exception. */
fun <Config : KSolverConfiguration> observeSolverQueries(
    solver: KSolver<Config>,
    observer: (USolverQueryEvent) -> Unit,
): KSolver<Config> = ObservedSolver(solver = solver, observer = observer)

internal class ObservedSolver<Config : KSolverConfiguration>(
    private val solver: KSolver<Config>,
    private val observer: (USolverQueryEvent) -> Unit,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : KSolver<Config> by solver {
    override fun check(timeout: Duration): KSolverStatus = measured { solver.check(timeout = timeout) }

    override fun checkWithAssumptions(
        assumptions: List<KExpr<KBoolSort>>,
        timeout: Duration,
    ): KSolverStatus = measured { solver.checkWithAssumptions(assumptions = assumptions, timeout = timeout) }

    @Suppress("TooGenericExceptionCaught") // Report every backend failure and rethrow the same exception.
    private inline fun measured(block: () -> KSolverStatus): KSolverStatus {
        val started = timeSource.markNow()
        var status: KSolverStatus? = null
        var failure: String? = null
        try {
            val result = block()
            status = result
            return result
        } catch (error: Throwable) {
            failure = error::class.java.name
            throw error
        } finally {
            val event = USolverQueryEvent(
                elapsedNanos = started.elapsedNow().inWholeNanoseconds,
                status = status,
                failure = failure,
            )
            runCatching { observer(event) }
                .onFailure { error -> logger.warn(error) { "Solver query observer failed while recording an event" } }
        }
    }
}
