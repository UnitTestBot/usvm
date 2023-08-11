package org.usvm.samples.strings11

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.disableTest
import org.usvm.util.isException

class StringConcatTest : JavaMethodTestRunner() {
    @Test
    fun testConcatArguments() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredProperties(
            StringConcat::concatArguments,
            eq(1),
            { _, a, b, c, r -> "$a$b$c" == r }
        )
    }

    @Test
    fun testConcatWithConstants() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
        checkDiscoveredProperties(
            StringConcat::concatWithConstants,
            eq(4),
            { _, a, r -> a == "head" && r == 1 },
            { _, a, r -> a == "body" && r == 2 },
            { _, a, r -> a == null && r == 3 },
            { _, a, r -> a != "head" && a != "body" && a != null && r == 4 },
        )
    }

    @Disabled("Flickers too much with JVM 17")
    @Test
    fun testConcatWithPrimitives() {
        checkDiscoveredProperties(
            StringConcat::concatWithPrimitives,
            eq(1),
            { _, a, r -> "$a#4253.0" == r }
        )
    }

    @Test
    fun testExceptionInToString() = disableTest("Some properties were not discovered at positions (from 0): [1]") {
        checkDiscoveredPropertiesWithExceptions(
            StringConcat::exceptionInToString,
            ignoreNumberOfAnalysisResults,
            { _, t, r -> t.x == 42 && r.isException<IllegalArgumentException>() },
            { _, t, r -> t.x != 42 && r.getOrThrow() == "Test: x = ${t.x}!" },
        )
    }

    // TODO unsupported
//    @Test
//    fun testConcatWithField() {
//        checkWithThis(
//            StringConcat::concatWithField,
//            eq(1),
//            { _, o, a, r -> "$a${o.str}#" == r }
//        )
//    }

    @Test
    fun testConcatWithPrimitiveWrappers() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredProperties(
            StringConcat::concatWithPrimitiveWrappers,
            ignoreNumberOfAnalysisResults,
            { _, b, c, r -> b.toString().endsWith("4") && c == '2' && r == 1 },
            { _, _, c, r -> !c.toString().endsWith("42") && r == 2 },
        )
    }

    @Test
    fun testSameConcat() {
        checkDiscoveredProperties(
            StringConcat::sameConcat,
            ignoreNumberOfAnalysisResults,
            { _, a, b, r -> a == b && r == 0 },
            { _, a, b, r -> a != b && r == 1 },
        )
    }

    @Test
    fun testConcatStrangeSymbols() {
        checkDiscoveredProperties(
            StringConcat::concatStrangeSymbols,
            eq(1),
            { _, r -> r == "\u0000#\u0001!\u0002@\u0012\t" }
        )
    }
}