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
        )
    }

    @Test
    fun testReturnSet() {
        checkExecutionMatches(
            DeepEqualsTestingClass::returnSet,
        )
    }

    @Test
    fun testReturnMap() {
        checkExecutionMatches(
            DeepEqualsTestingClass::returnMap,
        )
    }

    @Test
    fun testReturnArray() {
        checkExecutionMatches(
            DeepEqualsTestingClass::returnArray,
        )
    }

    @Test
    @Disabled("We do not support 2d generics containers right now")
    fun testReturn2DList() {
        checkExecutionMatches(
            DeepEqualsTestingClass::return2DList,
        )
    }

    @Test
    @Disabled("We do not support 2d generics containers right now")
    fun testReturn2DSet() {
        checkExecutionMatches(
            DeepEqualsTestingClass::return2DSet,
        )
    }

    @Test
    @Disabled("We do not support 2d generics containers right now")
    fun testReturn2DMap() {
        checkExecutionMatches(
            DeepEqualsTestingClass::return2DMap,
        )
    }

    @Test
    @Disabled("We do not support 2d generics containers right now")
    fun testIntegers2DList() {
        checkExecutionMatches(
            DeepEqualsTestingClass::returnIntegers2DList,
        )
    }

    @Test
    fun testReturn2DArray() {
        checkExecutionMatches(
            DeepEqualsTestingClass::return2DArray,
        )
    }

    @Test
    fun testReturnCommonClass() {
        checkExecutionMatches(
            DeepEqualsTestingClass::returnCommonClass,
        )
    }

    @Test
    fun testTriangle() {
        checkExecutionMatches(
            DeepEqualsTestingClass::returnTriangle,
        )
    }

    @Test
    fun testQuadrilateral() {
        checkExecutionMatches(
            DeepEqualsTestingClass::returnQuadrilateralFromNode,
        )
    }

    @Test
    fun testIntMultiArray() {
        checkExecutionMatches(
            DeepEqualsTestingClass::fillIntMultiArrayWithConstValue,
        )
    }

    @Test
    fun testDoubleMultiArray() {
        checkExecutionMatches(
            DeepEqualsTestingClass::fillDoubleMultiArrayWithConstValue,
        )
    }

    @Test
    fun testIntegerWrapperMultiArray() {
        checkExecutionMatches(
            DeepEqualsTestingClass::fillIntegerWrapperMultiArrayWithConstValue,
        )
    }

    @Test
    fun testDoubleWrapperMultiArray() {
        checkExecutionMatches(
            DeepEqualsTestingClass::fillDoubleWrapperMultiArrayWithConstValue,
        )
    }
}