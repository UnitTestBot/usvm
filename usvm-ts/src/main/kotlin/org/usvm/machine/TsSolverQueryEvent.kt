package org.usvm.machine

import io.ksmt.expr.KExpr
import io.ksmt.solver.KSolver
import io.ksmt.solver.KSolverConfiguration
import io.ksmt.solver.KSolverStatus
import io.ksmt.sort.KBoolSort
import kotlin.time.Duration
import kotlin.time.TimeSource

/** One actual call to the SMT backend, excluding assertion/model decoding time. */
data class TsSolverQueryEvent(
    val elapsedNanos: Long,
    val status: KSolverStatus?,
    val failure: String?,
)

internal fun <Config : KSolverConfiguration> observeTsSolverQueries(
    solver: KSolver<Config>,
    observer: (TsSolverQueryEvent) -> Unit,
): KSolver<Config> = ObservedTsSolver(solver, observer)

private class ObservedTsSolver<Config : KSolverConfiguration>(
    private val delegate: KSolver<Config>,
    private val observer: (TsSolverQueryEvent) -> Unit,
) : KSolver<Config> by delegate {
    override fun check(timeout: Duration): KSolverStatus = measured { delegate.check(timeout) }

    override fun checkWithAssumptions(
        assumptions: List<KExpr<KBoolSort>>,
        timeout: Duration,
    ): KSolverStatus = measured { delegate.checkWithAssumptions(assumptions, timeout) }

    @Suppress("TooGenericExceptionCaught") // Observe failures without changing the backend exception.
    private inline fun measured(block: () -> KSolverStatus): KSolverStatus {
        val started = TimeSource.Monotonic.markNow()
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
            observer(
                TsSolverQueryEvent(
                    elapsedNanos = started.elapsedNow().inWholeNanoseconds,
                    status = status,
                    failure = failure,
                ),
            )
        }
    }
}
