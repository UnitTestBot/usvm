package org.usvm.samples.unsafe

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner


internal class UnsafeOperationsTest : JavaMethodTestRunner() {
    @Test
    fun checkGetAddressSizeOrZero() {
            checkExecutionMatches(
                UnsafeOperations::getAddressSizeOrZero,
                { _, r -> r > 0 },
            )
    }

    // TODO unsupported
//    @Test
//    fun checkGetAddressSizeOrZeroWithMocks() {
//        withoutSandbox {
//            checkExecutionMatches(
//                UnsafeOperations::getAddressSizeOrZero,
//                eq(1),
//                { _, r -> r!! > 0 },
//                // Coverage matcher fails (branches: 0/0, instructions: 15/21, lines: 0/0)
//                // TODO: check coverage calculation: https://github.com/UnitTestBot/UTBotJava/issues/807,
//                mockStrategy = MockStrategyApi.OTHER_CLASSES
//            )
//        }
//    }
}