package org.usvm.ps.statistics

import org.usvm.ApplicationGraph

class StepsCountingStoppingStrategy<Method, Statement>(
    graph: ApplicationGraph<Method, Statement>,
) : Statistics<Method, Statement>(graph) {
    private var stepsCounterSinceLastTerminatedState: Int = 0

    fun shouldDrop(): Boolean = stepsCounterSinceLastTerminatedState >= STEPS_LIMIT

    override fun onMethodVisit(method: Method) {
        // Do nothing
    }

    override fun onStatementVisit(statement: Statement) {
        stepsCounterSinceLastTerminatedState++
    }

    override fun onStatementCovered(statement: Statement) {
        stepsCounterSinceLastTerminatedState = 0
    }

    override fun recalculate() {
        // Do nothing
    }

    companion object {
        const val STEPS_LIMIT = 3500
    }
}
