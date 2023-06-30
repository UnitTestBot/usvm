package org.usvm.samples.objects

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import kotlin.reflect.KFunction3

internal class ObjectWithPrimitivesClassTest : JavaMethodTestRunner() {
// TODO kfunction0
    //    @Test
//    fun testDefaultConstructor() {
//        val method: KFunction0<ObjectWithPrimitivesClass> = ::ObjectWithPrimitivesClass
//        checkExecutionMatches(
//            method,
//            eq(1),
//            // TODO: SAT-933 Add support for constructor testing")
//            // { instance -> instance is ObjectWithPrimitivesClass },
//        )
//    }

    @Test
    @Disabled("Required value was null. at org.usvm.samples.JavaMethodTestRunner\$runner\$1.invoke(JavaMethodTestRunner.kt:516)")
    fun testConstructorWithParams() {
        val method: KFunction3<Int, Int, Double, ObjectWithPrimitivesClass> = ::ObjectWithPrimitivesClass
        checkDiscoveredProperties(
            method,
            eq(1),
//            { _, x, y, weight, instance ->
//                instance is ObjectWithPrimitivesClass && instance.x == x && instance.y == y && instance.weight == weight
//            },
        )
    }
}
