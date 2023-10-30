package org.usvm.util

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class JvmStopwatch : Stopwatch {
    private var startTimeMs = 0L
    private var stopTimeMs = 0L

    override val elapsed: Duration
        get() = (stopTimeMs - startTimeMs).milliseconds

    override var isRunning: Boolean = false
        private set

    override fun start() {
        if (isRunning) {
            return
        }
        isRunning = true
        startTimeMs = System.currentTimeMillis()
    }

    override fun stop() {
        if (!isRunning) {
            return
        }
        isRunning = false
        stopTimeMs = System.currentTimeMillis()
    }

    override fun reset() {
        isRunning = false
        startTimeMs = 0L
        stopTimeMs = 0L
    }
}
