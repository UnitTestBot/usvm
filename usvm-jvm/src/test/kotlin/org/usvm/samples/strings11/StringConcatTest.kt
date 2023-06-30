package org.usvm.samples.strings11

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.isException

class StringConcatTest : JavaMethodTestRunner() {
    @Test
    @Disabled("Unexpected lvalue org.usvm.machine.JcStaticFieldRef@1d628a88")
    fun testConcatArguments() {
        checkDiscoveredProperties(
            StringConcat::concatArguments,
            eq(1),
            { _, a, b, c, r -> "$a$b$c" == r }
        )
    }

    @Test
    @Disabled("Unexpected lvalue org.usvm.machine.JcStaticFieldRef@1d628a88")
    fun testConcatWithConstants() {
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
    @Disabled("Unexpected lvalue org.usvm.machine.JcStaticFieldRef@78181f7f")
    fun testExceptionInToString() {
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
    @Disabled("Unexpected lvalue org.usvm.machine.JcStaticFieldRef@549fc0b3")
    fun testConcatWithPrimitiveWrappers() {
        checkDiscoveredProperties(
            StringConcat::concatWithPrimitiveWrappers,
            ignoreNumberOfAnalysisResults,
            { _, b, c, r -> b.toString().endsWith("4") && c == '2' && r == 1 },
            { _, _, c, r -> !c.toString().endsWith("42") && r == 2 },
        )
    }

    @Test
    @Disabled("Unexpected lvalue org.usvm.machine.JcStaticFieldRef@7e8e111d")
    fun testSameConcat() {
        checkDiscoveredProperties(
            StringConcat::sameConcat,
            ignoreNumberOfAnalysisResults,
            { _, a, b, r -> a == b && r == 0 },
            { _, a, b, r -> a != b && r == 1 },
        )
    }

    @Test
    @Disabled("An operation is not implemented: Not yet implemented")
    fun testConcatStrangeSymbols() {
        checkDiscoveredProperties(
            StringConcat::concatStrangeSymbols,
            eq(1),
            { _, r -> r == "\u0000#\u0001!\u0002@\u0012\t" }
        )
    }
}