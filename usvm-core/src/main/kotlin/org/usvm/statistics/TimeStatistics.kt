package org.usvm.statistics

import org.usvm.util.JvmStopwatch

class TimeStatistics {
    private val stopwatch = JvmStopwatch()

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
