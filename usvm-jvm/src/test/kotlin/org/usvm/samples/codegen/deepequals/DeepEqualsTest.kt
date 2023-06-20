package org.usvm.samples.codegen.deepequals

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


import org.usvm.test.util.checkers.eq


class DeepEqualsTest : JavaMethodTestRunner() {
    @Test
    fun testReturnList() {
        this.checkDiscoveredProperties(
            DeepEqualsTestingClass::returnList,
            eq(1),
        )
    }

    @Test
    fun testReturnSet() {
        this.checkDiscoveredProperties(
            DeepEqualsTestingClass::returnSet,
            eq(1),
        )
    }

    @Test
    fun testReturnMap() {
        this.checkDiscoveredProperties(
            DeepEqualsTestingClass::returnMap,
            eq(1),
        )
    }

    @Test
    fun testReturnArray() {
        this.checkDiscoveredProperties(
            DeepEqualsTestingClass::returnArray,
            eq(1),
        )
    }

    @Test
    @Disabled("We do not support 2d generics containers right now")
    fun testReturn2DList() {
        this.checkDiscoveredProperties(
            DeepEqualsTestingClass::return2DList,
            eq(1),
        )
    }

    @Test
    @Disabled("We do not support 2d generics containers right now")
    fun testReturn2DSet() {
        this.checkDiscoveredProperties(
            DeepEqualsTestingClass::return2DSet,
            eq(1),
        )
    }

    @Test
    @Disabled("We do not support 2d generics containers right now")
    fun testReturn2DMap() {
        this.checkDiscoveredProperties(
            DeepEqualsTestingClass::return2DMap,
            eq(1),
        )
    }

    @Test
    @Disabled("We do not support 2d generics containers right now")
    fun testIntegers2DList() {
        this.checkDiscoveredProperties(
            DeepEqualsTestingClass::returnIntegers2DList,
            eq(1),
        )
    }

    @Test
    fun testReturn2DArray() {
        this.checkDiscoveredProperties(
            DeepEqualsTestingClass::return2DArray,
            eq(1),
        )
    }

    @Test
    fun testReturnCommonClass() {
        this.checkDiscoveredProperties(
            DeepEqualsTestingClass::returnCommonClass,
            eq(1),
        )
    }

    @Test
    fun testTriangle() {
        checkDiscoveredProperties(
            DeepEqualsTestingClass::returnTriangle,
            eq(1),
        )
    }

    @Test
    fun testQuadrilateral() {
        checkDiscoveredProperties(
            DeepEqualsTestingClass::returnQuadrilateralFromNode,
            eq(1),
        )
    }

    @Test
    fun testIntMultiArray() {
        checkDiscoveredProperties(
            DeepEqualsTestingClass::fillIntMultiArrayWithConstValue,
            eq(3),
        )
    }

    @Test
    fun testDoubleMultiArray() {
        checkDiscoveredProperties(
            DeepEqualsTestingClass::fillDoubleMultiArrayWithConstValue,
            eq(3),
        )
    }

    @Test
    fun testIntegerWrapperMultiArray() {
        checkDiscoveredProperties(
            DeepEqualsTestingClass::fillIntegerWrapperMultiArrayWithConstValue,
            eq(3),
        )
    }

    @Test
    fun testDoubleWrapperMultiArray() {
        checkDiscoveredProperties(
            DeepEqualsTestingClass::fillDoubleWrapperMultiArrayWithConstValue,
            eq(3),
        )
    }
}