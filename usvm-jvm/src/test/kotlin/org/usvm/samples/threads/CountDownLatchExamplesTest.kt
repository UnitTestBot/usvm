package org.usvm.samples.threads

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


class CountDownLatchExamplesTest : JavaMethodTestRunner() {
    @Test
    @Disabled("Unable to make field private final java.util.concurrent.CountDownLatch\$Sync java.util.concurrent.CountDownLatch.sync accessible: module java.base does not \"opens java.util.concurrent\" to unnamed module @399f45b1\n")
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
