package org.usvm.util

import kotlin.time.Duration

interface Stopwatch {
    fun start()
    fun stop()
    fun reset()

    val elapsed: Duration
    val isRunning: Boolean
}
