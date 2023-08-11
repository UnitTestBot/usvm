package org.usvm.samples.casts


import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.util.disableTest


internal class CastClassTest : JavaMethodTestRunner() {
    @Test
    fun testThisTypeChoice() = disableTest("Support assumptions") {
        checkDiscoveredProperties(
            CastClass::castToInheritor,
            eq(0),
        )
    }
}