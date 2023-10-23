package org.usvm.samples.approximations

import approximations.java.lang.StringBuffer_Tests
import approximations.java.util.ArrayListSpliterator_Tests
import approximations.java.util.ArrayList_Tests
import approximations.java.util.HashSet_Tests
import approximations.java.util.OptionalDouble_Tests
import approximations.java.util.OptionalInt_Tests
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.usvm.test.util.checkers.ge
import kotlin.reflect.KFunction1
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.jvm.javaMethod

class ApproximationsTest : ApproximationsTestRunner() {

    @ParameterizedTest
    @MethodSource("approximationTests")
    fun testApproximations(test: KFunction1<Int, Int>, testAnnotation: approximations.Test) {
        System.err.println("-".repeat(50))
        System.err.println("Start: $test")
        val properties = Array(testAnnotation.executionMax) { idx -> { o: Int, _: Result<Int> -> o == idx } }
        checkDiscoveredPropertiesWithExceptions(
            test,
            ge(testAnnotation.executionMax + 2),
            *properties,
            invariants = arrayOf({ execution, r ->
                execution !in 0..testAnnotation.executionMax || r.getOrThrow() == execution
            })
        )
    }

    companion object {
        @JvmStatic
        fun approximationTestClasses() = listOf(
            StringBuffer_Tests::class,
            ArrayList_Tests::class,
            ArrayListSpliterator_Tests::class,
            HashSet_Tests::class,
            OptionalDouble_Tests::class,
            OptionalInt_Tests::class
        )

        @JvmStatic
        fun approximationTests(): List<Arguments> =
            approximationTestClasses().flatMap { cls ->
                cls.declaredFunctions
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
            }
    }
}
