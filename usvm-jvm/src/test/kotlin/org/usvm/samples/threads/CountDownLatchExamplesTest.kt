package org.usvm.samples.threads

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


class CountDownLatchExamplesTest : JavaMethodTestRunner() {
    @Test
    fun testGetAndDown() {
        checkExecutionMatches(
            CountDownLatchExamples::getAndDown,
            { _, countDownLatch, l -> countDownLatch.count == 0L && l == 0L },
            { _, countDownLatch, l ->
                val firstCount = countDownLatch.count

                firstCount != 0L && l == firstCount - 1
            },
        )
    }
}
