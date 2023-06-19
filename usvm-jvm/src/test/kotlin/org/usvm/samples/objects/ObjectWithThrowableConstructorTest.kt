package org.usvm.samples.objects

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq

import kotlin.reflect.KFunction2

internal class ObjectWithThrowableConstructorTest : JavaMethodTestRunner() {
    @Test
    @Disabled("SAT-1500 Support verification of UtAssembleModel for possible exceptions")
    fun testThrowableConstructor() {
        val method: KFunction2<Int, Int, ObjectWithThrowableConstructor> = ::ObjectWithThrowableConstructor
        checkExecutionMatches(
            method,
            // TODO: SAT-933 Add support for constructor testing
        )
    }
}
