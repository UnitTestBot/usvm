package org.usvm.samples.math

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.util.isException
import kotlin.math.abs
import kotlin.math.hypot

@Suppress("SimplifyNegatedBinaryExpression")
internal class DoubleFunctionsTest : JavaMethodTestRunner() {
    @Test
    @Tag("slow")
    fun testHypo() {
        checkDiscoveredProperties(
            DoubleFunctions::hypo,
            eq(1),
            { _, a, b, r -> a > 1 && a < 10 && b > 1 && b < 10 && abs(r - hypot(a, b)) < 1e-5 },
        )
    }

    @Test
    fun testMax() {
        checkDiscoveredProperties(
            DoubleFunctions::max,
            eq(2),
            { _, a, b, r -> a > b && r == a },
            { _, a, b, r -> !(a > b) && (r == b || r.isNaN()) }
        )
    }

    @Test
    @Tag("slow")
    fun testCircleSquare() {
        checkDiscoveredPropertiesWithExceptions(
            DoubleFunctions::circleSquare,
            eq(5),
            { _, radius, r -> radius < 0 && r.isException<IllegalArgumentException>() },
            { _, radius, r -> radius > 10000 && r.isException<IllegalArgumentException>() },
            { _, radius, r -> radius.isNaN() && r.isException<IllegalArgumentException>() },
            { _, radius, r -> Math.PI * radius * radius <= 777.85 && r.getOrNull() == 0.0 },
            { _, radius, r -> Math.PI * radius * radius > 777.85 && abs(777.85 - r.getOrNull()!!) >= 1e-5 }
        )
    }

    @Test
    @Tag("slow")
    fun testNumberOfRootsInSquareFunction() {
        checkDiscoveredProperties(
            DoubleFunctions::numberOfRootsInSquareFunction,
            eq(3), // sometimes solver substitutes a = nan || b = nan || c = nan || some combination of 0 and inf
            { _, a, b, c, r -> !(b * b - 4 * a * c >= 0) && r == 0 },
            { _, a, b, c, r -> b * b - 4 * a * c == 0.0 && r == 1 },
            { _, a, b, c, r -> b * b - 4 * a * c > 0 && r == 2 },
        )
    }
}