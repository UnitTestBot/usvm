package org.usvm.utils

import org.usvm.solver.USatResult
import org.usvm.solver.USolverResult

fun <T> USolverResult<T>.ensureSat(): USatResult<T> {
    check(this is USatResult) { "Expected SAT result, but got $this" }
    return this
}
