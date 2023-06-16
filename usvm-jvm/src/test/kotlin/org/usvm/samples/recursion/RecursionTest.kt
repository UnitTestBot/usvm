package org.usvm.samples.recursion

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.between
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ge
import org.usvm.util.isException


import kotlin.math.pow

// TODO Kotlin mocks generics https://github.com/UnitTestBot/UTBotJava/issues/88
internal class RecursionTest : JavaMethodTestRunner() {
    @Test
    fun testFactorial() {
        checkWithExceptionExecutionMatches(
            Recursion::factorial,
            eq(3),
            { _, x, r -> x < 0 && r.isException<IllegalArgumentException>() },
            { _, x, r -> x == 0 && r.getOrNull() == 1 },
            { _, x, r -> x > 0 && r.getOrNull() == (1..x).reduce { a, b -> a * b } }
        )
    }

    @Test
    fun testFib() {
        checkWithExceptionExecutionMatches(
            Recursion::fib,
            eq(4),
            { _, x, r -> x < 0 && r.isException<IllegalArgumentException>() },
            { _, x, r -> x == 0 && r.getOrNull() == 0 },
            { _, x, r -> x == 1 && r.getOrNull() == 1 },
            { _, x, r -> x > 1 && r.getOrNull() == Recursion().fib(x) }
        )
    }

    @Test
    @Disabled("Freezes the execution when snd != 0 JIRA:1293")
    fun testSum() {
        checkExecutionMatches(
            Recursion::sum,
            eq(2),
            { _, x, y, r -> y == 0 && r == x },
            { _, x, y, r -> y != 0 && r == x + y }
        )
    }

    @Test
    fun testPow() {
        checkWithExceptionExecutionMatches(
            Recursion::pow,
            eq(4),
            { _, _, y, r -> y < 0 && r.isException<IllegalArgumentException>() },
            { _, _, y, r -> y == 0 && r.getOrNull() == 1 },
            { _, x, y, r -> y % 2 == 1 && r.getOrNull() == x.toDouble().pow(y.toDouble()).toInt() },
            { _, x, y, r -> y % 2 != 1 && r.getOrNull() == x.toDouble().pow(y.toDouble()).toInt() }
        )
    }

    @Test
    fun infiniteRecursionTest() {
        checkWithExceptionExecutionMatches(
            Recursion::infiniteRecursion,
            eq(2),
            { _, x, r -> x > 10000 && r.isException<StackOverflowError>() },
            { _, x, r -> x <= 10000 && r.isException<StackOverflowError>() },
        )
    }

    @Test
    fun vertexSumTest() {
        checkExecutionMatches(
            Recursion::vertexSum,
            between(2..3),
            { _, x, _ -> x <= 10 },
            { _, x, _ -> x > 10 }
        )
    }

    @Test
    fun recursionWithExceptionTest() {
        checkWithExceptionExecutionMatches(
            Recursion::recursionWithException,
            ge(3),
            { _, x, r -> x < 42 && r.isException<IllegalArgumentException>() },
            { _, x, r -> x == 42 && r.isException<IllegalArgumentException>() },
            { _, x, r -> x > 42 && r.isException<IllegalArgumentException>() },
        )
    }

    @Test
    fun recursionLoopTest() {
        checkExecutionMatches(
            Recursion::firstMethod,
            eq(2),
            { _, x, _ -> x < 4 },
            { _, x, _ -> x >= 4 },
        )
    }
}