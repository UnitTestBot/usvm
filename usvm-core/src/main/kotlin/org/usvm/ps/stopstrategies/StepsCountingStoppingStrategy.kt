package org.usvm.ps.stopstrategies

import org.usvm.ps.statistics.Statistics
import org.usvm.statistics.ApplicationGraph

class StepsCountingStoppingStrategy<Method, Statement>(
    graph: ApplicationGraph<Method, Statement>,
    val stepsLimit: Int = STEPS_LIMIT
) : Statistics<Method, Statement>(graph), StoppingStrategy {
    private var stepsCounterSinceLastTerminatedState: Int = 0

    override fun shouldStop(): Boolean = stepsCounterSinceLastTerminatedState >= stepsLimit

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
        const val STEPS_LIMIT = 1_000
    }
}
