package org.usvm.samples.exceptions

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.util.disableTest


internal class JvmCrashExamplesTest : JavaMethodTestRunner() {
    @Test
    fun testExit() = disableTest("skipMethodInvocation: Sequence has more than one element") {
        checkDiscoveredProperties(
            JvmCrashExamples::exit,
            eq(2)
        )
    }

    @Test
    fun testCrash() = disableTest("Expected exactly 1 executions, but 417 found") {
        checkDiscoveredProperties(
            JvmCrashExamples::crash, // we expect only one execution after minimization
            eq(1)
            // It seems that we can't calculate coverage when the child JVM has crashed
        )
    }

    // TODO unsupported
//    @Test
//    fun testCrashPrivileged() {
//        checkExecutionMatches(
//            JvmCrashExamples::crashPrivileged,
//            eq(1), // we expect only one execution after minimization
//            // It seems that we can't calculate coverage when the child JVM has crashed
//        )
//    }
}
