package org.usvm.statistics

import org.usvm.util.JvmStopwatch

/**
 * Maintains information about time spent on machine processes.
 */
class TimeStatistics {
    private val stopwatch = JvmStopwatch()

    /**
     * Total machine running time.
     */
    val runningTime get() = stopwatch.elapsed

    fun onMachineStarted() {
        check(!stopwatch.isRunning) { "onMachineStarted was called multiple times" }
        stopwatch.start()
    }

    fun onMachineStopped() {
        check(stopwatch.isRunning) { "onMachineStarted was not called" }
        stopwatch.stop()
    }

    fun reset() = stopwatch.reset()
}
