package org.usvm.statistics.constraints

import org.usvm.UState
import org.usvm.utils.applySoftConstraints
import org.usvm.statistics.UMachineObserver
import java.util.concurrent.atomic.AtomicBoolean

class SoftConstraintsObserver<Type, State : UState<Type, *, *, *, *, State>> : UMachineObserver<State> {
    override fun onStateTerminated(state: State, stateReachable: Boolean, isConsumed: AtomicBoolean) {
        if (stateReachable) {
            // TODO actually, only states presented in CoveredNewStatesCollector should be here,
            //  so for now soft constraints are applied for more states than required. Rewrite it after refactoring
            //  path selector factory, observers, collectors, etc.
            state.applySoftConstraints()
        }
    }
}
