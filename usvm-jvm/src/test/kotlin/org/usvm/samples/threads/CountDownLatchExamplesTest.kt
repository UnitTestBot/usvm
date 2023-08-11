package org.usvm.samples.threads

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.util.disableTest


class CountDownLatchExamplesTest : JavaMethodTestRunner() {
    @Test
    fun testGetAndDown() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
        checkDiscoveredProperties(
            CountDownLatchExamples::getAndDown,
            eq(2),
            { _, countDownLatch, l -> countDownLatch.count == 0L && l == 0L },
            { _, countDownLatch, l ->
                val firstCount = countDownLatch.count

                firstCount != 0L && l == firstCount - 1
            },
        )
    }
}
