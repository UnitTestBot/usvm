package org.usvm.statistics

import org.usvm.UState
import org.usvm.util.StopwatchImpl
import kotlin.time.Duration

/**
 * Maintains information about time spent on machine processes.
 */
class TimeStatistics<Method, State : UState<*, Method, *, *, *, State>> : UMachineObserver<State> {
    private val stopwatch = StopwatchImpl()
    private val methodStopwatch = StopwatchImpl()

    private val methodTimes = mutableMapOf<Method, Duration>()

    /**
     * Total machine running time.
     */
    val runningTime get() = stopwatch.elapsed

    /**
     * Returns time spent by machine on [method].
     */
    fun getTimeSpentOnMethod(method: Method) = methodTimes.getOrDefault(method, Duration.ZERO)

    override fun onMachineStarted() {
        check(!stopwatch.isRunning)
        stopwatch.start()
    }

    override fun onMachineStopped() {
        check(stopwatch.isRunning)
        stopwatch.stop()
        methodStopwatch.stop()
    }

    override fun onStatePeeked(state: State) {
        check(!methodStopwatch.isRunning)
        methodStopwatch.start()
    }

    override fun onState(parent: State, forks: Sequence<State>) {
        check(methodStopwatch.isRunning)
        methodStopwatch.stop()
        // TODO: measure time for all visited methods, not only for entrypoints
        val entrypoint = parent.entrypoint
        methodTimes.merge(entrypoint, methodStopwatch.elapsed) { current, elapsed -> current + elapsed }
        methodStopwatch.reset()
    }
}
