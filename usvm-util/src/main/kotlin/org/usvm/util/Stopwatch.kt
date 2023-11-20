package org.usvm.util

import kotlin.time.Duration

/**
 * Measures elapsed time.
 */
interface Stopwatch {
    /**
     * Starts time measurement.
     */
    fun start()

    /**
     * Stops time measurement. Current measurement is not discarded so
     * [start] can be run again.
     */
    fun stop()

    /**
     * Stops time measurement and discards results.
     */
    fun reset()

    /**
     * Total elapsed time.
     */
    val elapsed: Duration

    /**
     * Indicates if time measurement is in process.
     */
    val isRunning: Boolean
}
