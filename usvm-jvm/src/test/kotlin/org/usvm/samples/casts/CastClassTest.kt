package org.usvm.samples.casts


import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


internal class CastClassTest : JavaMethodTestRunner() {
    @Test
    fun testThisTypeChoice() {
        checkDiscoveredProperties(
            CastClass::castToInheritor,
            eq(0),
        )
    }
}