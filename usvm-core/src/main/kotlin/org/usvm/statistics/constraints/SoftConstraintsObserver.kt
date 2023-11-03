package org.usvm.statistics.constraints

import org.usvm.UState
import org.usvm.solver.USoftConstraintsProvider
import org.usvm.solver.USolverBase
import org.usvm.statistics.UMachineObserver
import org.usvm.utils.applySoftConstraints

class SoftConstraintsObserver<Type, State : UState<Type, *, *, *, *, State>>(
    val solver: USolverBase<Type>,
    val softConstraintsProvider: USoftConstraintsProvider<Type, *>,
) : UMachineObserver<State> {
    override fun onStateTerminated(state: State, stateReachable: Boolean) {
        if (stateReachable) {
            // TODO actually, only states presented in CoveredNewStatesCollector should be here,
            //  so for now soft constraints are applied for more states than required. Rewrite it after refactoring
            //  path selector factory, observers, collectors, etc.
            state.applySoftConstraints(solver, softConstraintsProvider)
        }
    }
}
