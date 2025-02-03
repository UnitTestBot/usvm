package org.usvm.samples.casts

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


internal class ArrayCastExampleTest : JavaMethodTestRunner() {
    @Suppress("KotlinConstantConditions")
    @Test
    fun testCastToAncestor() {
        checkDiscoveredProperties(
            ArrayCastExample::castToAncestor,
            eq(2),
            { _, a, r -> a == null && r != null && r is Array<CastClass> },
            { _, a, r -> a != null && r != null && r.isArrayOf<CastClassFirstSucc>() }
        )
    }

    @Test
    fun testClassCastException() {
        checkDiscoveredProperties(
            ArrayCastExample::classCastException,
            eq(3),
            { _, a, r -> a == null && r != null && r.isEmpty() },
            { _, a, _ -> !a.isArrayOf<CastClassFirstSucc>() },
            { _, a, r -> a.isArrayOf<CastClassFirstSucc>() && r != null && r.isArrayOf<CastClassFirstSucc>() },
        )
    }

    @Test
    fun testNullCast() {
        checkDiscoveredProperties(
            ArrayCastExample::nullCast,
            eq(2),
            { _, a, r -> a != null && r == null },
            { _, a, r -> a == null && r == null }
        )
    }

    @Test
    fun testNullArray() {
        checkDiscoveredProperties(
            ArrayCastExample::nullArray,
            eq(1),
            { _, r -> r == null }
        )
    }

    @Test
    fun testSuccessfulExampleFromJLS() {
        checkDiscoveredProperties(
            ArrayCastExample::successfulExampleFromJLS,
            eq(1),
            { _, r ->
                require(r != null)

                val sizeConstraint = r.size == 4
                val typeConstraint = r[0] is ColoredPoint && r[1] is ColoredPoint
                val zeroElementConstraint = r[0].x == 2 && r[0].y == 2 && r[0].color == 12
                val firstElementConstraint = r[1].x == 4 && r[1].y == 5 && r[1].color == 24

                sizeConstraint && typeConstraint && zeroElementConstraint && firstElementConstraint
            }
        )
    }

    @Test
    fun testCastAfterStore() {
        checkDiscoveredProperties(
            ArrayCastExample::castAfterStore,
            eq(5),
            { _, a, _ -> a == null },
            { _, a, _ -> a.isEmpty() },
            { _, a, _ -> a.isNotEmpty() && !a.isArrayOf<ColoredPoint>() },
            { _, a, _ -> a.isArrayOf<ColoredPoint>() && a.size == 1 },
            { _, a, r ->
                require(r != null)

                val sizeConstraint = a.size >= 2
                val typeConstraint = a.isArrayOf<ColoredPoint>() && r.isArrayOf<ColoredPoint>()
                val zeroElementConstraint = r[0].color == 12 && r[0].x == 1 && r[0].y == 2
                val firstElementConstraint = r[1].color == 14 && r[1].x == 2 && r[1].y == 3

                sizeConstraint && typeConstraint && zeroElementConstraint && firstElementConstraint
            }
        )
    }

    @Test
    fun testCastFromObject() {
        checkDiscoveredProperties(
            ArrayCastExample::castFromObject,
            eq(3),
            { _, a, _ -> a !is Array<*> || !a.isArrayOf<CastClassFirstSucc>() },
            { _, a, r -> a == null && r != null && r.isArrayOf<CastClassFirstSucc>() && r.isEmpty() },
            { _, a, r -> a is Array<*> && a.isArrayOf<CastClassFirstSucc>() && r != null && r.isArrayOf<CastClassFirstSucc>() },
        )
    }

    @Test
    fun testCastFromObjectToPrimitivesArray() {
        checkDiscoveredProperties(
            ArrayCastExample::castFromObjectToPrimitivesArray,
            eq(2),
            { _, array, r -> array is IntArray && array.size > 0 && r is IntArray && array contentEquals r },
            { _, array, r -> array != null && array !is IntArray && r !is IntArray },
        )
    }

    @Test
    fun testCastsChainFromObject() {
        checkDiscoveredProperties(
            ArrayCastExample::castsChainFromObject,
            eq(8),
            { _, a, r -> a == null && r == null },
            { _, a, _ -> a !is Array<*> || !a.isArrayOf<Point>() },
            { _, a, r -> a is Array<*> && a.isArrayOf<Point>() && a.isEmpty() && r == null },
            { _, a, _ -> a is Array<*> && a.isArrayOf<Point>() && a.isNotEmpty() && a[0] == null },
            { _, a, _ -> a is Array<*> && a.isArrayOf<Point>() && !a.isArrayOf<ColoredPoint>() && (a[0] as Point).x == 1 },
            { _, a, _ -> a is Array<*> && a.isArrayOf<Point>() && !a.isArrayOf<ColoredPoint>() && (a[0] as Point).x != 1 },
            { _, a, r -> a is Array<*> && a.isArrayOf<ColoredPoint>() && (a[0] as Point).x == 1 && r != null && r[0].x == 10 },
            { _, a, r -> a is Array<*> && a.isArrayOf<ColoredPoint>() && (a[0] as Point).x != 1 && r != null && r[0].x == 5 },
        )
    }

    @Test
    fun testCastFromCollections() {
        checkDiscoveredProperties(
            ArrayCastExample::castFromCollections,
            eq(3),
            { _, c, r -> c == null && r == null },
            { _, c, r -> c != null && c is List<*> && r is List<*> },
            { _, c, _ -> c is Collection<*> && c !is List<*> },
        )
    }

    @Test
    @Disabled("TODO: randomly fail due to type selection issues") // todo: use instantiatable types in test resolver
    fun testCastFromIterable() {
        checkDiscoveredProperties(
            ArrayCastExample::castFromIterable,
            eq(3),
            { _, i, r -> i == null && r == null },
            { _, i, r -> i is List<*> && r is List<*> },
            { _, i, _ -> i is Iterable<*> && i !is List<*> },
        )
    }

    @Test
    fun testCastFromIterableToCollection() {
        checkDiscoveredProperties(
            ArrayCastExample::castFromIterableToCollection,
            eq(3),
            { _, i, r -> i == null && r == null },
            { _, i, r -> i is Collection<*> && r is Collection<*> },
            { _, i, _ -> i is Iterable<*> && i !is Collection<*> },
        )
    }
}