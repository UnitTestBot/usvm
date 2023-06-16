package org.usvm.samples.casts

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


import org.usvm.test.util.checkers.eq
import org.usvm.util.isException

internal class CastExampleTest : JavaMethodTestRunner() {
    @Test
    fun testSimpleCast() {
        checkExecutionMatches(
            CastExample::simpleCast,
            eq(3),
            { _, o, _ -> o != null && o !is CastClassFirstSucc },
            { _, o, r -> o != null && r is CastClassFirstSucc },
            { _, o, r -> o == null && r == null },
        )
    }

    @Test
    fun testClassCastException() {
        checkWithExceptionExecutionMatches(
            CastExample::castClassException,
            eq(3),
            { _, o, r -> o == null && r.isException<NullPointerException>() },
            { _, o, r -> o != null && o !is CastClassFirstSucc && r.isException<ClassCastException>() },
            { _, o, r -> o != null && o is CastClassFirstSucc && r.isException<ClassCastException>() },
        )
    }

    @Test
    fun testCastUp() {
        checkExecutionMatches(
            CastExample::castUp,
            eq(1)
        )
    }

    @Test
    fun testCastNullToDifferentTypes() {
        checkExecutionMatches(
            CastExample::castNullToDifferentTypes,
            eq(1)
        )
    }

    @Test
    fun testFromObjectToPrimitive() {
        checkExecutionMatches(
            CastExample::fromObjectToPrimitive,
            eq(3),
            { _, obj, _ -> obj == null },
            { _, obj, _ -> obj != null && obj !is Int },
            { _, obj, r -> obj != null && obj is Int && r == obj }
        )
    }

    @Test
    fun testCastFromObjectToInterface() {
        checkExecutionMatches(
            CastExample::castFromObjectToInterface,
            eq(2),
            { _, obj, _ -> obj != null && obj !is Colorable },
            { _, obj, r -> obj != null && obj is Colorable && r == obj },
        )
    }

    @Test
    fun testComplicatedCast() {
        checkExecutionMatches(
            CastExample::complicatedCast,
            eq(2),
            { _, i, a, _ -> i == 0 && a != null && a[i] != null && a[i] !is CastClassFirstSucc },
            { _, i, a, r -> i == 0 && a != null && a[i] != null && a[i] is CastClassFirstSucc && r is CastClassFirstSucc },
        )
    }
}