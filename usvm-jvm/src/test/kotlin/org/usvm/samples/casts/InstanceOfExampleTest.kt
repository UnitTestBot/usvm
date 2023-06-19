package org.usvm.samples.casts

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq

import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ge
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults


internal class InstanceOfExampleTest : JavaMethodTestRunner() {
    @Test
    fun testSimpleInstanceOf() {
        checkExecutionMatches(
            InstanceOfExample::simpleInstanceOf,
            { _, o, r -> o is CastClassFirstSucc && r is CastClassFirstSucc },
            { _, o, r -> o !is CastClassFirstSucc && r == null },
        )
    }

    @Test
    fun testNullPointerCheck() {
        checkExecutionMatches(
            InstanceOfExample::nullPointerCheck,
            { _, o, _ -> o == null },
            { _, o, r -> o is CastClassFirstSucc && r == o.z },
            { _, o, r -> o !is CastClassFirstSucc && o != null && r == o.x },
        )
    }

    @Test
    fun testVirtualCall() {
        checkExecutionMatches(
            InstanceOfExample::virtualCall,
            { _, o, r -> o is CastClassFirstSucc && r == o.foo() },
            { _, o, r -> o !is CastClassFirstSucc && r == -1 },
        )
    }

    @Test
    fun testVirtualFunctionCallWithCast() {
        checkExecutionMatches(
            InstanceOfExample::virtualFunctionCallWithCast,
            { _, o, r -> o !is CastClassFirstSucc && r == -1 },
            { _, o, _ -> o is CastClass && o !is CastClassFirstSucc },
            { _, o, r -> o is CastClassFirstSucc && r == o.z },
        )
    }

    @Test
    fun testVirtualCallWithoutOneInheritor() {
        checkExecutionMatches(
            InstanceOfExample::virtualCallWithoutOneInheritor,
            { _, o, r -> o !is CastClassFirstSucc && o is CastClass && r == o.foo() },
            { _, o, r -> o is CastClassSecondSucc && r == o.foo() },
            { _, o, _ -> o == null },
            { _, o, r -> o is CastClassFirstSucc && r == o.foo() },
        )
    }

    @Test
    fun testVirtualCallWithoutOneInheritorInverse() {
        checkExecutionMatches(
            InstanceOfExample::virtualCallWithoutOneInheritorInverse,
            { _, o, r -> o !is CastClassFirstSucc && o is CastClass && r == o.foo() },
            { _, o, r -> o is CastClassSecondSucc && r == o.foo() },
            { _, o, _ -> o == null },
            { _, o, r -> o is CastClassFirstSucc && r == o.foo() },
        )
    }

    @Test
    fun testWithoutOneInheritorOnArray() {
        checkExecutionMatches(
            InstanceOfExample::withoutOneInheritorOnArray,
            { _, o, r -> o.isInstanceOfArray<CastClassFirstSucc>() && r == 0 },
            { _, o, r -> !o.isInstanceOfArray<CastClassFirstSucc>() && r == 1 },
        )
    }

    @Test
    fun testWithoutOneInheritorOnArrayInverse() {
        checkExecutionMatches(
            InstanceOfExample::withoutOneInheritorOnArrayInverse,
            { _, o, r -> !o.isInstanceOfArray<CastClassFirstSucc>() && r == 0 },
            { _, o, r -> o.isInstanceOfArray<CastClassFirstSucc>() && r == 1 },
        )
    }


    @Test
    fun testInstanceOfAsPartOfInternalExpressions() {
        checkExecutionMatches(
            InstanceOfExample::instanceOfAsPartOfInternalExpressions,
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
    fun testInstanceOfAsPartOfInternalExpressionsCastClass() {
        checkExecutionMatches(
            InstanceOfExample::instanceOfAsPartOfInternalExpressionsCastClass,
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
        checkExecutionMatches(
            InstanceOfExample::instanceOfAsPartOfInternalExpressionsXor,
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
        checkExecutionMatches(
            InstanceOfExample::instanceOfAsPartOfInternalExpressionsXorInverse,
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
        checkExecutionMatches(
            InstanceOfExample::instanceOfAsPartOfInternalExpressionsIntValue,
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
    fun testInstanceOfAsInternalExpressionsMap() {
        checkExecutionMatches(
            InstanceOfExample::instanceOfAsInternalExpressionsMap,
        )
    }


    @Test
    fun testSymbolicInstanceOf() {
        checkExecutionMatches(
            InstanceOfExample::symbolicInstanceOf,
            { _, _, i, r -> i < 1 && r == null },
            { _, _, i, r -> i > 3 && r == null },
            { _, o, _, _ -> o == null },
            { _, o, i, _ -> o != null && i > o.lastIndex },
            { _, o, i, r -> o != null && o[i] is CastClassFirstSucc && r is CastClassFirstSucc },
            { _, o, i, r -> o != null && o[i] !is CastClassFirstSucc && r is CastClassSecondSucc },
        )
    }

    @Test
    //TODO: fails without concrete execution
    fun testComplicatedInstanceOf() {
        checkExecutionMatches(
            InstanceOfExample::complicatedInstanceOf,
            { _, _, index, _, result -> index < 0 && result == null },
            { _, _, index, _, result -> index > 2 && result == null },
            { _, objects, index, _, result -> index in 0..2 && objects == null && result == null },
            { _, objects, index, _, result -> index in 0..2 && objects != null && objects.size < index + 2 && result == null },
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
        checkExecutionMatches(
            InstanceOfExample::instanceOfFromArray,
            { _, a, _ -> a == null },
            { _, a, r -> a.size != 3 && r == null },
            { _, a, r -> a.size == 3 && a[0] is CastClassFirstSucc && r != null && r[0] is CastClassFirstSucc },
            { _, a, r -> a.size == 3 && a[0] is CastClassSecondSucc && r != null && r[0] == null },
            { _, a, r -> a.size == 3 && a[0] !is CastClassFirstSucc && a[0] !is CastClassSecondSucc && r != null },
        )
    }

    @Test
    fun testInstanceOfFromArrayWithReadingAnotherElement() {
        checkExecutionMatches(
            InstanceOfExample::instanceOfFromArrayWithReadingAnotherElement,
            { _, a, _ -> a == null },
            { _, a, r -> a != null && a.size < 2 && r == null },
            { _, a, r -> a != null && a.size >= 2 && a[0] is CastClassFirstSucc && r is CastClassFirstSucc },
            { _, a, r -> a != null && a.size >= 2 && a[0] !is CastClassFirstSucc && r == null },
        )
    }

    @Test
    fun testInstanceOfFromArrayWithReadingSameElement() {
        checkExecutionMatches(
            InstanceOfExample::instanceOfFromArrayWithReadingSameElement,
            { _, a, _ -> a == null },
            { _, a, r -> a != null && a.size < 2 && r == null },
            { _, a, r -> a != null && a.size >= 2 && a[0] is CastClassFirstSucc && r is CastClassFirstSucc },
            { _, a, r -> a != null && a.size >= 2 && a[0] !is CastClassFirstSucc && r == null },
        )
    }

    @Test
    fun testIsNull() {
        checkExecutionMatches(
            InstanceOfExample::isNull,
            { _, a, r -> a is Array<*> && a.isArrayOf<Number>() && r == 1 },
            { _, a, r -> a == null && r == 2 },
        )
    }

    @Test
    fun testArrayInstanceOfArray() {
        checkExecutionMatches(
            InstanceOfExample::arrayInstanceOfArray,
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
        checkExecutionMatches(
            InstanceOfExample::objectInstanceOfArray,
            { _, a, r -> a is IntArray && r is IntArray && a contentEquals r },
            { _, a, r -> a is BooleanArray && r is BooleanArray && a contentEquals r },
            { _, a, r -> (a == null && r == null) || (!(a is IntArray || a is BooleanArray) && a.equals(r)) },
        )
    }

    @Test
    fun testInstanceOfObjectArray() {
        checkExecutionMatches(
            InstanceOfExample::instanceOfObjectArray,
            { _, a, r -> a == null && r == null },
            { _, a, r -> a is Array<*> && a.isArrayOf<Array<IntArray>>() && r is Array<*> && r contentDeepEquals a },
            { _, a, r -> a is Array<*> && !a.isArrayOf<Array<IntArray>>() && r!!::class == a::class },
        )
    }


    private inline fun <reified T : Any> Any?.isInstanceOfArray() =
        (this as? Array<*>)?.run { T::class.java.isAssignableFrom(this::class.java.componentType) } == true
}