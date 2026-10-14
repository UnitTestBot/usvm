package org.usvm.machine

import org.usvm.UPathSelector
import org.usvm.machine.state.TsState
import org.usvm.statistics.UMachineObserver

/**
 * Collects unfinished inputs without changing the machine's ordinary witness collection.
 *
 * Install this as the interpreter observer and pass [createMachineObserver] as the machine observer factory.
 * Call [collectRemainingStates] after a successful analysis, while the machine is still open. Queue extraction
 * is deferred because `onMachineStopped` also runs on exceptional exits, and draining changes stop metadata.
 * No additional execution steps or satisfiability checks are performed for queued states.
 */
class TsStoppedStatesObserver(
    private val consumeState: (TsState) -> Unit,
) : TsInterpreterObserver {
    private var pathSelector: UPathSelector<TsState>? = null
    private var machineStopped: Boolean = false
    private var emissionFailure: Throwable? = null

    fun createMachineObserver(pathSelector: UPathSelector<TsState>): UMachineObserver<TsState> {
        this.pathSelector = pathSelector
        machineStopped = false
        emissionFailure = null

        return object : UMachineObserver<TsState> {
            override fun onMachineStopped() {
                machineStopped = true
            }

            override fun onStateTerminated(state: TsState, stateReachable: Boolean) {
                val isStoppedPrefix = stateReachable && state.unsupportedReason != null
                if (isStoppedPrefix) {
                    consumeState(state.clone())
                }
            }
        }
    }

    override fun onPathStopped(state: TsState, reason: String) {
        // Unknown-call observation must not interrupt fallback pruning; report sink failures on completion.
        val emission = runCatching {
            val prefix = state.clone()
            prefix.unsupportedReason = reason
            consumeState(prefix)
        }
        val failure = emission.exceptionOrNull()
        if (failure != null && emissionFailure == null) {
            emissionFailure = failure
        }
    }

    fun collectRemainingStates() {
        val failure = emissionFailure
        if (failure != null) {
            throw failure
        }

        val selector = pathSelector ?: return
        check(machineStopped) { "Collect queued states only after successful machine analysis" }

        selector.drain().forEach(consumeState)
    }
}
