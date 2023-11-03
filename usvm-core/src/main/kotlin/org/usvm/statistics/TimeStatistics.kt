package org.usvm.statistics

import org.usvm.UState
import org.usvm.util.JvmStopwatch
import kotlin.time.Duration

/**
 * Maintains information about time spent on machine processes.
 */
class TimeStatistics<Method, State : UState<*, Method, *, *, *, State>> : UMachineObserver<State> {
    private val stopwatch = JvmStopwatch()
    private val solverStopwatch = JvmStopwatch()
    private val methodStopwatch = JvmStopwatch()
    private val methodSolverStopwatch = JvmStopwatch()

    private val methodTimes = mutableMapOf<Method, Duration>().withDefault { Duration.ZERO }
    private val methodSolverTimes = mutableMapOf<Method, Duration>().withDefault { Duration.ZERO }

    /**
     * Total number of steps machine made.
     */
    var totalSteps = 0UL
        private set

    private val methodSteps = mutableMapOf<Method, ULong>().withDefault { 0UL }

    /**
     * Total machine running time.
     */
    val runningTime get() = stopwatch.elapsed

    /**
     * Total time spent by SMT solver.
     */
    val solverTime get() = solverStopwatch.elapsed

    /**
     * Returns time spent by machine on [method].
     */
    fun getTimeSpentOnMethod(method: Method) = methodTimes.getValue(method)

    /**
     * Returns time spent by machine on [method].
     */
    fun getSolverTimeSpentOnMethod(method: Method) = methodSolverTimes.getValue(method)

    /**
     * Returns number of steps machine made during [method] exploration.
     */
    fun getMethodSteps(method: Method) = methodSteps.getValue(method)

    fun onSolverStarted() {
        check(!solverStopwatch.isRunning)
        check(!methodSolverStopwatch.isRunning)
        solverStopwatch.start()
        methodSolverStopwatch.start()
    }

    fun onSolverStopped() {
        check(solverStopwatch.isRunning)
        check(methodSolverStopwatch.isRunning)
        solverStopwatch.stop()
        methodSolverStopwatch.stop()
    }

    override fun onMachineStarted() {
        check(!stopwatch.isRunning)
        check(!methodStopwatch.isRunning)
        stopwatch.start()
        methodStopwatch.start()
    }

    override fun onMachineStopped() {
        check(stopwatch.isRunning)
        stopwatch.stop()
    }

    override fun onStatePeeked(state: State) {
        methodStopwatch.reset()
        methodSolverStopwatch.reset()
        methodStopwatch.start()
    }

    override fun onState(parent: State, forks: Sequence<State>) {
        methodStopwatch.stop()
        methodTimes[parent.entrypoint] = methodTimes.getValue(parent.entrypoint) + methodStopwatch.elapsed
        methodSolverTimes[parent.entrypoint] = methodSolverTimes.getValue(parent.entrypoint) + methodSolverStopwatch.elapsed

        totalSteps++
        methodSteps[parent.entrypoint] = methodSteps.getValue(parent.entrypoint) + 1UL
    }

    companion object {
        private var timeStatisticsForSolver: TimeStatistics<*, *>? = null

        fun configureTimeStatisticsForSolver(statistics: TimeStatistics<*, *>) {
            timeStatisticsForSolver = statistics
        }

        fun <T> runSolverMeasuringTime(action: () -> T): T {
            timeStatisticsForSolver?.onSolverStarted()
            try {
                return action()
            } finally {
                timeStatisticsForSolver?.onSolverStopped()
            }
        }
    }
}
