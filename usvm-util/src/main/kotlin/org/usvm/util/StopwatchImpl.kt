package org.usvm.util

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * [Stopwatch] implementation based on [System.currentTimeMillis] method.
 */
class StopwatchImpl : Stopwatch {
    private var startTime = Duration.ZERO

    override var elapsed: Duration = Duration.ZERO
        private set
        get() {
            if (!isRunning) {
                return field
            }
            return field + currentElapsed
        }

    override var isRunning: Boolean = false
        private set

    private val currentElapsed get() = System.currentTimeMillis().milliseconds - startTime

    override fun start() {
        if (isRunning) {
            return
        }
        isRunning = true
        startTime = System.currentTimeMillis().milliseconds
    }

    override fun stop() {
        if (!isRunning) {
            return
        }
        isRunning = false
        elapsed += currentElapsed
    }

    override fun reset() {
        isRunning = false
        startTime = Duration.ZERO
        elapsed = Duration.ZERO
    }
}
