package org.usvm.samples.approximations

import approximations.java.lang.StringBuffer_Tests
import approximations.java.util.ArrayList_Tests
import approximations.java.util.OptionalDouble_Tests
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.usvm.test.util.checkers.eq
import org.usvm.util.isException
import kotlin.reflect.KFunction1
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.jvm.javaMethod

class ApproximationsTest : ApproximationsTestRunner() {
    @Test
    fun testOptionalDouble() {
        checkDiscoveredPropertiesWithExceptions(
            OptionalDouble_Tests::test_of_0,
            eq(1),
            invariants = arrayOf(
                { execution, r -> r.getOrThrow() == execution }
            )
        )
    }

    @Test
    fun testArrayList() {
        checkDiscoveredPropertiesWithExceptions(
            ArrayList_Tests::test_get_0,
            eq(6),
            { o, r -> o == 0 && r.isException<IndexOutOfBoundsException>() },
            { o, _ -> o == 1 },
            { o, _ -> o == 2 },
            { o, _ -> o == 3 },
            { o, _ -> o == 4 },
            invariants = arrayOf(
                { execution, r -> execution !in 1..4 || r.getOrThrow() == execution }
            )
        )
    }

    @Test
    fun testStringBuffer() {
        checkDiscoveredPropertiesWithExceptions(
            StringBuffer_Tests::test_toString_0,
            eq(3),
            { o, _ -> o == 0 },
            { o, _ -> o == 1 },
            invariants = arrayOf(
                { execution, r -> execution !in 0..1 || r.getOrThrow() == execution }
            )
        )
    }

    @ParameterizedTest
    @MethodSource("stringBufferTests")
    fun tmp(test: KFunction1<Int, Int>, testAnnotation: approximations.Test) {
        val properties = Array(testAnnotation.executionMax) { idx -> { o: Int, _: Result<Int> -> o == idx } }
        checkDiscoveredPropertiesWithExceptions(
            test,
            eq(testAnnotation.executionMax + 2),
            *properties,
            invariants = arrayOf({ execution, r ->
                execution !in 0..testAnnotation.executionMax || r.getOrThrow() == execution
            })
        )
    }

    companion object {
        @JvmStatic
        fun stringBufferTests(): List<Arguments> =
            StringBuffer_Tests::class.declaredFunctions
                .asSequence()
                .filterIsInstance<KFunction1<*, *>>()
                .map {
                    @Suppress("UNCHECKED_CAST")
                    it as KFunction1<Int, Int>
                }
                .mapNotNull { test ->
                    val annotation = test.javaMethod?.getAnnotation(approximations.Test::class.java)
                    annotation?.let { test to it }
                }
                .filterNot { it.second.disabled }
                .map { (test, annotation) ->
                    Arguments.of(test, annotation)
                }
                .toList()
    }
}
