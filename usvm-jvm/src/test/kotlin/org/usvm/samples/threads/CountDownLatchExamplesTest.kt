package org.usvm.samples.threads

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


class CountDownLatchExamplesTest : JavaMethodTestRunner() {
    @Test
    @Disabled("java.lang.IllegalStateException: Sort mismatch. Support exceptions")
    fun testGetAndDown() {
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
