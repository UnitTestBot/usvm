package org.usvm.statistics

/** Reports an unexpected interpreter failure that discards a state. */
interface UStepFailureObserver<State> {
    fun onStepFailure(state: State, error: Throwable) { }
}
