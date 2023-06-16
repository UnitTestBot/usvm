package org.usvm.samples.codegen.deepequals

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


import org.usvm.test.util.checkers.eq


class DeepEqualsTest : JavaMethodTestRunner() {
    @Test
    fun testReturnList() {
        checkExecutionMatches(
            DeepEqualsTestingClass::returnList,
            eq(1),
        )
    }

    @Test
    fun testReturnSet() {
        checkExecutionMatches(
            DeepEqualsTestingClass::returnSet,
            eq(1),
        )
    }

    @Test
    fun testReturnMap() {
        checkExecutionMatches(
            DeepEqualsTestingClass::returnMap,
            eq(1),
        )
    }

    @Test
    fun testReturnArray() {
        checkExecutionMatches(
            DeepEqualsTestingClass::returnArray,
            eq(1),
        )
    }

    @Test
    @Disabled("We do not support 2d generics containers right now")
    fun testReturn2DList() {
        checkExecutionMatches(
            DeepEqualsTestingClass::return2DList,
            eq(1),
        )
    }

    @Test
    @Disabled("We do not support 2d generics containers right now")
    fun testReturn2DSet() {
        checkExecutionMatches(
            DeepEqualsTestingClass::return2DSet,
            eq(1),
        )
    }

    @Test
    @Disabled("We do not support 2d generics containers right now")
    fun testReturn2DMap() {
        checkExecutionMatches(
            DeepEqualsTestingClass::return2DMap,
            eq(1),
        )
    }

    @Test
    @Disabled("We do not support 2d generics containers right now")
    fun testIntegers2DList() {
        checkExecutionMatches(
            DeepEqualsTestingClass::returnIntegers2DList,
            eq(1),
        )
    }

    @Test
    fun testReturn2DArray() {
        checkExecutionMatches(
            DeepEqualsTestingClass::return2DArray,
            eq(1),
        )
    }

    @Test
    fun testReturnCommonClass() {
        checkExecutionMatches(
            DeepEqualsTestingClass::returnCommonClass,
            eq(1),
        )
    }

    @Test
    fun testTriangle() {
        checkExecutionMatches(
            DeepEqualsTestingClass::returnTriangle,
            eq(1),
        )
    }

    @Test
    fun testQuadrilateral() {
        checkExecutionMatches(
            DeepEqualsTestingClass::returnQuadrilateralFromNode,
            eq(1),
        )
    }

    @Test
    fun testIntMultiArray() {
        checkExecutionMatches(
            DeepEqualsTestingClass::fillIntMultiArrayWithConstValue,
            eq(3),
        )
    }

    @Test
    fun testDoubleMultiArray() {
        checkExecutionMatches(
            DeepEqualsTestingClass::fillDoubleMultiArrayWithConstValue,
            eq(3),
        )
    }

    @Test
    fun testIntegerWrapperMultiArray() {
        checkExecutionMatches(
            DeepEqualsTestingClass::fillIntegerWrapperMultiArrayWithConstValue,
            eq(3),
        )
    }

    @Test
    fun testDoubleWrapperMultiArray() {
        checkExecutionMatches(
            DeepEqualsTestingClass::fillDoubleWrapperMultiArrayWithConstValue,
            eq(3),
        )
    }
}