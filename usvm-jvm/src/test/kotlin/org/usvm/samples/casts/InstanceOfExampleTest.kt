package org.usvm.samples.casts

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ge
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

internal class InstanceOfExampleTest : JavaMethodTestRunner() {
    @Test
    fun testSimpleInstanceOf() {
        checkDiscoveredProperties(
            InstanceOfExample::simpleInstanceOf,
            eq(2),
            { _, o, r -> o is CastClassFirstSucc && r is CastClassFirstSucc },
            { _, o, r -> o !is CastClassFirstSucc && r == null },
        )
    }

    @Test
    fun testNullPointerCheck() {
        checkDiscoveredProperties(
            InstanceOfExample::nullPointerCheck,
            eq(3),
            { _, o, _ -> o == null },
            { _, o, r -> o is CastClassFirstSucc && r == o.z },
            { _, o, r -> o !is CastClassFirstSucc && o != null && r == o.x },
        )
    }

    @Test
    fun testVirtualCall() {
        checkDiscoveredProperties(
            InstanceOfExample::virtualCall,
            eq(2),
            { _, o, r -> o is CastClassFirstSucc && r == o.foo() },
            { _, o, r -> o !is CastClassFirstSucc && r == -1 },
        )
    }

    @Test
    fun testVirtualFunctionCallWithCast() {
        checkDiscoveredProperties(
            InstanceOfExample::virtualFunctionCallWithCast,
            eq(3),
            { _, o, r -> o !is CastClassFirstSucc && r == -1 },
            { _, o, _ -> o is CastClass && o !is CastClassFirstSucc },
            { _, o, r -> o is CastClassFirstSucc && r == o.z },
        )
    }

    @Test
    fun testVirtualCallWithoutOneInheritor() {
        checkDiscoveredProperties(
            InstanceOfExample::virtualCallWithoutOneInheritor,
            eq(4),
            { _, o, r -> o !is CastClassFirstSucc && o is CastClass && r == o.foo() },
            { _, o, r -> o is CastClassSecondSucc && r == o.foo() },
            { _, o, _ -> o == null },
            { _, o, r -> o is CastClassFirstSucc && r == o.foo() },
        )
    }

    @Test
    fun testVirtualCallWithoutOneInheritorInverse() {
        checkDiscoveredProperties(
            InstanceOfExample::virtualCallWithoutOneInheritorInverse,
            eq(4),
            { _, o, r -> o !is CastClassFirstSucc && o is CastClass && r == o.foo() },
            { _, o, r -> o is CastClassSecondSucc && r == o.foo() },
            { _, o, _ -> o == null },
            { _, o, r -> o is CastClassFirstSucc && r == o.foo() },
        )
    }

    @Test
    fun testWithoutOneInheritorOnArray() {
        checkDiscoveredProperties(
            InstanceOfExample::withoutOneInheritorOnArray,
            eq(2),
            { _, o, r -> o.isInstanceOfArray<CastClassFirstSucc>() && r == 0 },
            { _, o, r -> !o.isInstanceOfArray<CastClassFirstSucc>() && r == 1 },
        )
    }

    @Test
    fun testWithoutOneInheritorOnArrayInverse() {
        checkDiscoveredProperties(
            InstanceOfExample::withoutOneInheritorOnArrayInverse,
            eq(2),
            { _, o, r -> !o.isInstanceOfArray<CastClassFirstSucc>() && r == 0 },
            { _, o, r -> o.isInstanceOfArray<CastClassFirstSucc>() && r == 1 },
        )
    }


    @Test
    @Disabled("java.lang.ArrayStoreException: java.lang.Object. Support connection between array and element type")
    fun testInstanceOfAsPartOfInternalExpressions() {
        checkDiscoveredProperties(
            InstanceOfExample::instanceOfAsPartOfInternalExpressions,
            ignoreNumberOfAnalysisResults,
            { _, o, r ->
                val o0isFirst = o[0].isInstanceOfArray<CastClassFirstSucc>()
                val o1isSecond = o[1].isInstanceOfArray<CastClassSecondSucc>()
                val and = o0isFirst && o1isSecond
                and && r == 1
            },
            { _, o, r ->
                val o0isSecond = o[0].isInstanceOfArray<CastClassSecondSucc>()
                val o1isFirst = o[1].isInstanceOfArray<CastClassFirstSucc>()
                val or = o0isSecond || o1isFirst
                or && r == 2
            },
            { _, o, r ->
                val o0isFirst = o[0].isInstanceOfArray<CastClassFirstSucc>()
                val o1isSecond = o[1].isInstanceOfArray<CastClassSecondSucc>()

                val o0isSecond = o[0].isInstanceOfArray<CastClassSecondSucc>()
                val o1isFirst = o[1].isInstanceOfArray<CastClassFirstSucc>()

                val and = o0isFirst && o1isSecond
                val or = o0isSecond || o1isFirst

                !and && !or && r == 3
            },
        )
    }

    @Test
    @Disabled("TODO infinite execution, fix it.")
    fun testInstanceOfAsPartOfInternalExpressionsCastClass() {
        checkDiscoveredProperties(
            InstanceOfExample::instanceOfAsPartOfInternalExpressionsCastClass,
            ignoreNumberOfAnalysisResults,
            { _, o, r ->
                val o0isFirst = o[0].isInstanceOfArray<CastClass>()
                val o1isSecond = o[1].isInstanceOfArray<CastClass>()
                val and = o0isFirst && o1isSecond
                !and && r == 1
            },
            { _, o, r ->
                val o0isSecond = o[0].isInstanceOfArray<CastClassSecondSucc>()
                val o1isFirst = o[1].isInstanceOfArray<CastClassFirstSucc>()
                val or = o0isSecond || o1isFirst
                !or && r == 2
            },
            { _, o, r ->
                val o0isFirst = o[0].isInstanceOfArray<CastClass>()
                val o1isSecond = o[1].isInstanceOfArray<CastClass>()

                val o0isSecond = o[0].isInstanceOfArray<CastClassSecondSucc>()
                val o1isFirst = o[1].isInstanceOfArray<CastClassFirstSucc>()

                val and = o0isFirst && o1isSecond
                val or = o0isSecond || o1isFirst

                and && or && r == 3
            },
        )
    }

    @Test
    fun testInstanceOfAsPartOfInternalExpressionsXor() {
        checkDiscoveredProperties(
            InstanceOfExample::instanceOfAsPartOfInternalExpressionsXor,
            ge(5),
            { _, o, r -> (o == null || o.size != 2) && r == 0 },
            { _, o, r ->
                val o0isSecond = o[0].isInstanceOfArray<CastClassSecondSucc>()
                val o1isFirst = o[1].isInstanceOfArray<CastClassFirstSucc>()
                r == 1 && !o0isSecond && o1isFirst
            },
            { _, o, r ->
                val o0isSecond = o[0].isInstanceOfArray<CastClassSecondSucc>()
                val o1isFirst = o[1].isInstanceOfArray<CastClassFirstSucc>()
                r == 2 && o0isSecond && !o1isFirst
            },
            { _, o, r ->
                val o0isSecond = o[0].isInstanceOfArray<CastClassSecondSucc>()
                val o1isFirst = o[1].isInstanceOfArray<CastClassFirstSucc>()
                r == 3 && o0isSecond && o1isFirst
            },
            { _, o, r ->
                val o0isSecond = o[0].isInstanceOfArray<CastClassSecondSucc>()
                val o1isFirst = o[1].isInstanceOfArray<CastClassFirstSucc>()
                r == 4 && !o0isSecond && !o1isFirst
            },
        )
    }

    @Test
    fun testInstanceOfAsPartOfInternalExpressionsXorInverse() {
        checkDiscoveredProperties(
            InstanceOfExample::instanceOfAsPartOfInternalExpressionsXorInverse,
            ge(5),
            { _, o, r -> (o == null || o.size != 2) && r == 0 },
            { _, o, r ->
                val o0isSecond = o[0].isInstanceOfArray<CastClassSecondSucc>()
                val o1isFirst = o[1].isInstanceOfArray<CastClassFirstSucc>()
                r == 1 && o0isSecond && o1isFirst
            },
            { _, o, r ->
                val o0isSecond = o[0].isInstanceOfArray<CastClassSecondSucc>()
                val o1isFirst = o[1].isInstanceOfArray<CastClassFirstSucc>()
                r == 2 && !o0isSecond && !o1isFirst
            },
            { _, o, r ->
                val o0isSecond = o[0].isInstanceOfArray<CastClassSecondSucc>()
                val o1isFirst = o[1].isInstanceOfArray<CastClassFirstSucc>()
                r == 3 && o0isSecond && !o1isFirst
            },
            { _, o, r ->
                val o0isSecond = o[0].isInstanceOfArray<CastClassSecondSucc>()
                val o1isFirst = o[1].isInstanceOfArray<CastClassFirstSucc>()
                r == 4 && !o0isSecond && o1isFirst
            },
        )
    }

    @Test
    fun testInstanceOfAsPartOfInternalExpressionsIntValue() {
        checkDiscoveredProperties(
            InstanceOfExample::instanceOfAsPartOfInternalExpressionsIntValue,
            ignoreNumberOfAnalysisResults,
            { _, o, r ->
                val t1 = o.isInstanceOfArray<CastClass>()
                val t2 = !o.isInstanceOfArray<CastClassSecondSucc>()
                val t3 = r == 1
                t1 && t2 && t3
            },
            { _, o, r -> o.isInstanceOfArray<CastClassSecondSucc>() && r == 2 },
            { _, o, r -> !o.isInstanceOfArray<CastClass>() && r == 3 },
        )
    }

    @Test
    @Disabled("An operation is not implemented: Not yet implemented. Support strings/collections")
    fun testInstanceOfAsInternalExpressionsMap() {
        checkDiscoveredProperties(
            InstanceOfExample::instanceOfAsInternalExpressionsMap,
            ge(3),
        )
    }


    @Test
    fun testSymbolicInstanceOf() {
        checkDiscoveredProperties(
            InstanceOfExample::symbolicInstanceOf,
            ge(5),
            { _, _, i, r -> (i < 1 || i > 3) && r == null },
            { _, o, _, _ -> o == null },
            { _, o, i, _ -> o != null && i > o.lastIndex },
            { _, o, i, r -> o != null && o[i] is CastClassFirstSucc && r is CastClassFirstSucc },
            { _, o, i, r -> o != null && o[i] !is CastClassFirstSucc && r is CastClassSecondSucc },
        )
    }

    @Test
    @Disabled("Some properties were not discovered at positions (from 0): [4]. Support connection between array and element type")
    fun testComplicatedInstanceOf() {
        checkDiscoveredProperties(
            InstanceOfExample::complicatedInstanceOf,
            ge(5),
            { _, objects, index, _, result -> (index !in 0..2 || objects == null || objects.size >= index + 2) && result == null },
            { _, objects, index, objectExample, result ->
                require(objects != null && result != null && objectExample is CastClassFirstSucc)

                val sizeConstraint = index in 0..2 && objects.size >= index + 2
                val resultConstraint = result[index].x == objectExample.z

                sizeConstraint && resultConstraint
            },
            { _, objects, index, objectExample, _ ->
                index in 0..2 && objects != null && objects.size >= index + 2 && objectExample == null
            },
            { _, objects, index, objectExample, result ->
                require(objects != null && result != null && result[index] is CastClassSecondSucc)

                val sizeConstraint = index in 0..2 && objects.size >= index + 2
                val typeConstraint = objectExample !is CastClassFirstSucc && result[index] is CastClassSecondSucc
                val resultConstraint = result[index].x == result[index].foo()

                sizeConstraint && typeConstraint && resultConstraint
            },
            { _, objects, index, objectExample, result ->
                require(objects != null && result != null)

                val sizeConstraint = index in 0..2 && objects.size >= index + 2
                val objectExampleConstraint = objectExample !is CastClassFirstSucc
                val resultTypeConstraint = result[index] !is CastClassFirstSucc && result[index] !is CastClassSecondSucc
                val typeConstraint = objectExampleConstraint && resultTypeConstraint
                val resultConstraint = result[index].x == result[index].foo()

                sizeConstraint && typeConstraint && resultConstraint
            },
        )
    }

    @Test
    fun testInstanceOfFromArray() {
        checkDiscoveredProperties(
            InstanceOfExample::instanceOfFromArray,
            eq(5),
            { _, a, _ -> a == null },
            { _, a, r -> a.size != 3 && r == null },
            { _, a, r -> a.size == 3 && a[0] is CastClassFirstSucc && r != null && r[0] is CastClassFirstSucc },
            { _, a, r -> a.size == 3 && a[0] is CastClassSecondSucc && r != null && r[0] == null },
            { _, a, r -> a.size == 3 && a[0] !is CastClassFirstSucc && a[0] !is CastClassSecondSucc && r != null },
        )
    }

    @Test
    fun testInstanceOfFromArrayWithReadingAnotherElement() {
        checkDiscoveredProperties(
            InstanceOfExample::instanceOfFromArrayWithReadingAnotherElement,
            eq(4),
            { _, a, _ -> a == null },
            { _, a, r -> a != null && a.size < 2 && r == null },
            { _, a, r -> a != null && a.size >= 2 && a[0] is CastClassFirstSucc && r is CastClassFirstSucc },
            { _, a, r -> a != null && a.size >= 2 && a[0] !is CastClassFirstSucc && r == null },
        )
    }

    @Test
    fun testInstanceOfFromArrayWithReadingSameElement() {
        checkDiscoveredProperties(
            InstanceOfExample::instanceOfFromArrayWithReadingSameElement,
            eq(4),
            { _, a, _ -> a == null },
            { _, a, r -> a != null && a.size < 2 && r == null },
            { _, a, r -> a != null && a.size >= 2 && a[0] is CastClassFirstSucc && r is CastClassFirstSucc },
            { _, a, r -> a != null && a.size >= 2 && a[0] !is CastClassFirstSucc && r == null },
        )
    }

    @Test
    fun testIsNull() {
        checkDiscoveredProperties(
            InstanceOfExample::isNull,
            eq(2),
            { _, a, r -> a is Array<*> && a.isArrayOf<Number>() && r == 1 },
            { _, a, r -> a == null && r == 2 },
        )
    }

    @Test
    fun testArrayInstanceOfArray() {
        checkDiscoveredProperties(
            InstanceOfExample::arrayInstanceOfArray,
            eq(4),
            { _, a, r -> a == null && r == null },
            { _, a, r -> a is Array<*> && a.isArrayOf<Int>() && r is Array<*> && r.isArrayOf<Int>() },
            { _, a, r -> a is Array<*> && a.isArrayOf<Double>() && r is Array<*> && r.isArrayOf<Double>() },
            { _, a, r ->
                a is Array<*> && a.isArrayOf<Number>() && !a.isArrayOf<Int>() &&
                        !a.isArrayOf<Double>() && r is Array<*> && a contentDeepEquals r
            },
        )
    }

    @Test
    fun testObjectInstanceOfArray() {
        checkDiscoveredProperties(
            InstanceOfExample::objectInstanceOfArray,
            eq(3),
            { _, a, r -> a is IntArray && r is IntArray && a contentEquals r },
            { _, a, r -> a is BooleanArray && r is BooleanArray && a contentEquals r },
            { _, a, r -> (a == null && r == null) || (!(a is IntArray || a is BooleanArray) && a.equals(r)) },
        )
    }

    @Test
    fun testInstanceOfObjectArray() {
        checkDiscoveredProperties(
            InstanceOfExample::instanceOfObjectArray,
            eq(3),
            { _, a, r -> a == null && r == null },
            { _, a, r -> a is Array<*> && a.isArrayOf<Array<IntArray>>() && r is Array<*> && r contentDeepEquals a },
            { _, a, r -> a is Array<*> && !a.isArrayOf<Array<IntArray>>() && r != null && r::class == a::class },
        )
    }


    private inline fun <reified T : Any> Any?.isInstanceOfArray() =
        (this as? Array<*>)?.run { T::class.java.isAssignableFrom(this::class.java.componentType) } == true
}