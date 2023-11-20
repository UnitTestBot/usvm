package org.usvm.test

import org.junit.jupiter.api.Test
import org.usvm.util.RealTimeStopwatch
import java.util.concurrent.TimeUnit

class RealTimeStopwatchTests {

    @Test
    fun smokeTest() {
        val stopwatch = RealTimeStopwatch()
        stopwatch.start()
        TimeUnit.MILLISECONDS.sleep(400)
        stopwatch.stop()
        assert(stopwatch.elapsed.inWholeMilliseconds in 300..500)
    }

    @Test
    fun elapsedWithoutStopTest() {
        val stopwatch = RealTimeStopwatch()
        stopwatch.start()
        TimeUnit.MILLISECONDS.sleep(400)
        assert(stopwatch.elapsed.inWholeMilliseconds in 300..1000)
    }

    @Test
    fun multiplePeriodsTest() {
        val stopwatch = RealTimeStopwatch()
        stopwatch.start()
        TimeUnit.MILLISECONDS.sleep(400)
        stopwatch.stop()
        TimeUnit.MILLISECONDS.sleep(500)
        stopwatch.start()
        TimeUnit.MILLISECONDS.sleep(100)
        stopwatch.stop()
        assert(stopwatch.elapsed.inWholeMilliseconds in 450..600)
    }
}
