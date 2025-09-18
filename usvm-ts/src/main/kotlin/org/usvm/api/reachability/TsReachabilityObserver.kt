package org.usvm.api.reachability

import org.usvm.machine.state.TsState
import org.usvm.statistics.UMachineObserver

class TsReachabilityObserver : UMachineObserver<TsState> {
    override fun onState(parent: TsState, forks: Sequence<TsState>) {
        parent
            .targets
            .filter { it is TsReachabilityTarget }
            .forEach { target ->
                if (target.location == parent.pathNode.parent?.statement) {
                    target.propagate(parent)
                }
            }

        forks.forEach { fork ->
            fork
                .targets
                .filter { it is TsReachabilityTarget }
                .forEach { target ->
                    if (target.location == fork.pathNode.parent?.statement) {
                        target.propagate(fork)
                    }
                }
        }
    }

    override fun onStateTerminated(state: TsState, stateReachable: Boolean) {
        state.targets
            .filter { it is TsReachabilityTarget }
            .forEach { target ->
                if (target.location == state.pathNode.statement) {
                    target.propagate(state)
                }
            }
    }
}
