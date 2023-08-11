package org.usvm.samples.objects

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.util.disableTest
import kotlin.reflect.KFunction2

internal class ObjectWithThrowableConstructorTest : JavaMethodTestRunner() {
    @Test
    fun testThrowableConstructor() = disableTest("Support constructors in matchers") {
        val method: KFunction2<Int, Int, ObjectWithThrowableConstructor> = ::ObjectWithThrowableConstructor
        checkDiscoveredProperties(
            method,
            eq(2),
            // TODO: SAT-933 Add support for constructor testing
        )
    }
}
