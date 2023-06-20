package org.usvm.samples.exceptions

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


internal class JvmCrashExamplesTest : JavaMethodTestRunner() {
    @Test
    @Disabled("JIRA:1527")
    fun testExit() {
        checkDiscoveredProperties(
            JvmCrashExamples::exit,
            eq(2)
        )
    }

    @Test
    fun testCrash() {
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
