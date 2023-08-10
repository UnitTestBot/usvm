package org.usvm.samples.unsafe

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


internal class UnsafeOperationsTest : JavaMethodTestRunner() {
    @Test
    @Disabled("Can't find method (id:1)java.lang.Thread#getThreadGroup() in type java.lang.Object")
    fun checkGetAddressSizeOrZero() {
            checkDiscoveredProperties(
                UnsafeOperations::getAddressSizeOrZero,
                eq(1),
                { _, r -> r != null && r > 0 },
            )
    }

    // TODO unsupported
//    @Test
//    fun checkGetAddressSizeOrZeroWithMocks() {
//        withoutSandbox {
//            checkExecutionMatches(
//                UnsafeOperations::getAddressSizeOrZero,
//                eq(1),
//                { _, r -> r != null && r > 0 },
//                // Coverage matcher fails (branches: 0/0, instructions: 15/21, lines: 0/0)
//                mockStrategy = MockStrategyApi.OTHER_CLASSES
//            )
//        }
//    }
}