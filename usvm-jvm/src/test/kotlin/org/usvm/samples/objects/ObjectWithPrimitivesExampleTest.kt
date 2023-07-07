package org.usvm.samples.objects

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.isException


internal class ObjectWithPrimitivesExampleTest : JavaMethodTestRunner() {
    @Test
    fun testMax() {
        checkDiscoveredPropertiesWithExceptions(
            ObjectWithPrimitivesExample::max,
            ignoreNumberOfAnalysisResults,
            { _, fst, _, r -> fst == null && r.isException<NullPointerException>() },
            { _, _, snd, r -> snd == null && r.isException<NullPointerException>() },
            { _, fst, snd, r -> fst != null && snd != null && fst.x > snd.x && fst.y > snd.y && r.getOrNull()!! == fst },
            { _, fst, snd, r -> fst != null && snd != null && fst.x < snd.x && fst.y < snd.y && r.getOrNull()!! == snd },
            { _, fst, snd, r -> fst != null && snd != null && (fst.x == snd.x || fst.y == snd.y) && r.getOrNull()!! == fst },
        )
    }

    @Test
    fun testIgnoredInputParameters() {
        checkDiscoveredProperties(
            ObjectWithPrimitivesExample::ignoredInputParameters,
            eq(1),
            { _, fst, snd, r -> fst == null && snd == null && r != null }
        )
    }

    @Test
    fun testExample() {
        checkDiscoveredProperties(
            ObjectWithPrimitivesExample::example,
            eq(3),
            { _, v, _ -> v == null },
            { _, v, r -> v != null && v.x == 1 && r?.x == 1 },
            { _, v, r -> v != null && v.x != 1 && r?.x == 1 },
        )
    }

    // TODO unsupported
//    @Test
//    fun testExampleMutation() {
//        checkParamsMutations(
//            ObjectWithPrimitivesExample::example,
//            ignoreNumberOfAnalysisResults,
//            { valueBefore, valueAfter -> valueBefore.x != 0 && valueAfter.x == 1 }
//        )
//    }

    @Test
    fun testDefaultValueForSuperclassFields() {
        checkDiscoveredProperties(
            ObjectWithPrimitivesExample::defaultValueForSuperclassFields,
            eq(1),
            { _, r -> r != null && r.x == 0 && r.y == 0 && r.weight == 0.0 && r.valueByDefault == 5 && r.anotherX == 0 },
        )
    }

    @Test
    fun testCreateObject() {
        checkDiscoveredPropertiesWithExceptions(
            ObjectWithPrimitivesExample::createObject,
            eq(3),
            { _, _, _, o, r -> o == null && r.isException<NullPointerException>() },
            { _, _, _, o, r -> o != null && o.weight < 0 && r.isException<IllegalArgumentException>() },
            { _, a, b, o, r ->
                val result = r.getOrNull()!!

                val objectConstraint = o != null && (o.weight >= 0 || o.weight.isNaN())
                val resultConstraint = result.x == a + 5 && result.y == b + 6
                val postcondition = result.weight == o.weight || result.weight.isNaN() && o.weight.isNaN()

                objectConstraint && resultConstraint && postcondition
            },
        )
    }

    @Test
    fun testMemory() {
        checkDiscoveredPropertiesWithExceptions(
            ObjectWithPrimitivesExample::memory,
            eq(4),
            { _, o, v, r -> o == null && v > 0 && r.isException<NullPointerException>() },
            { _, o, v, r ->
                val resultValue = r.getOrNull()
                val objectToCompare = if (resultValue is ObjectWithPrimitivesClassSucc) {
                    ObjectWithPrimitivesClassSucc(1, 2, 1.2, resultValue.anotherX)
                } else {
                    ObjectWithPrimitivesClass(1, 2, 1.2)
                }
                objectToCompare.valueByDefault = resultValue!!.valueByDefault

                o != null && v > 0 && resultValue == objectToCompare
            },
            { _, o, v, r -> o == null && v <= 0 && r.isException<NullPointerException>() },
            { _, o, v, r ->
                val resultValue = r.getOrNull()
                val objectToCompare = if (resultValue is ObjectWithPrimitivesClassSucc) {
                    ObjectWithPrimitivesClassSucc(-1, -2, -1.2, resultValue.anotherX)
                } else {
                    ObjectWithPrimitivesClass(-1, -2, -1.2)
                }
                objectToCompare.valueByDefault = resultValue!!.valueByDefault

                o != null && v <= 0 && resultValue == objectToCompare
            },
        )
    }

    @Test
    fun testCompareWithNull() {
        checkDiscoveredProperties(
            ObjectWithPrimitivesExample::compareWithNull,
            eq(3),
            { _, fst, _, r -> fst == null && r == 1 },
            { _, fst, snd, r -> fst != null && snd == null && r == 2 },
            { _, fst, snd, r -> fst != null && snd != null && r == 3 },
        )
    }

    @Test
    fun testCompareTwoNullObjects() {
        checkDiscoveredProperties(
            ObjectWithPrimitivesExample::compareTwoNullObjects,
            eq(1),
        )
    }

    @Test
    fun testNullExample() {
        checkDiscoveredProperties(
            ObjectWithPrimitivesExample::nullExample,
            ignoreNumberOfAnalysisResults,
            { _, o, _ -> o == null },
            { _, o, r -> o != null && o.x != 0 && r != null },
            { _, o, r -> o != null && o.x == 0 && o.y != 0 && r != null },
            { _, o, r -> o != null && o.x == 0 && o.y == 0 && r == null },
        )
    }

    @Test
    fun testCompareTwoOuterObjects() {
        checkDiscoveredPropertiesWithExceptions(
            ObjectWithPrimitivesExample::compareTwoOuterObjects,
            eq(4),
            { _, x, _, r -> x == null && r.isException<NullPointerException>() },
            { _, x, y, r -> x != null && y == null && r.isException<NullPointerException>() },
            { _, x, y, r -> x != null && y != null && x === y && r.getOrNull() == true },
            { _, x, y, r -> x != null && y != null && x !== y && r.getOrNull() == false }
        )
    }

    @Test
    fun testCompareObjectWithArgument() {
        checkDiscoveredProperties(
            ObjectWithPrimitivesExample::compareObjectWithArgument,
            eq(1),
        )
    }

    @Test
    fun testCompareTwoDifferentObjects() {
        checkDiscoveredProperties(
            ObjectWithPrimitivesExample::compareTwoDifferentObjects,
            eq(1),
        )
    }


    @Test
    fun testCompareTwoIdenticalObjectsFromArguments() {
        checkDiscoveredPropertiesWithExceptions(
            ObjectWithPrimitivesExample::compareTwoIdenticalObjectsFromArguments,
            ignoreNumberOfAnalysisResults,
            { _, fst, _, r -> fst == null && r.isException<NullPointerException>() },
            { _, _, snd, r -> snd == null && r.isException<NullPointerException>() },
            { _, fst, snd, r -> fst != null && snd != null && r.getOrNull() == 1 },
            { _, fst, snd, r -> fst != null && snd != null && r.getOrNull() == 2 },
        )
    }

    @Test
    fun testCompareTwoRefEqualObjects() {
        checkDiscoveredProperties(
            ObjectWithPrimitivesExample::compareTwoRefEqualObjects,
            eq(1),
        )
    }

    @Test
    fun testGetOrDefault() {
        checkDiscoveredPropertiesWithExceptions(
            ObjectWithPrimitivesExample::getOrDefault,
            ignoreNumberOfAnalysisResults,
            { _, _, d, r -> d == null && r.isException<NullPointerException>() },
            { _, _, d, r -> d != null && d.x == 0 && d.y == 0 && r.isException<IllegalArgumentException>() },
            { _, o, d, r -> o == null && (d.x != 0 || d.y != 0) && r.getOrNull() == d },
            { _, o, d, r -> o != null && (d.x != 0 || d.y != 0) && r.getOrNull() == o },
        )
    }

    @Test
    fun testInheritorsFields() {
        checkDiscoveredPropertiesWithExceptions(
            ObjectWithPrimitivesExample::inheritorsFields,
            ignoreNumberOfAnalysisResults,
            { _, fst, _, r -> fst == null && r.isException<NullPointerException>() },
            { _, fst, snd, r -> fst != null && snd == null && r.isException<NullPointerException>() },
            { _, fst, snd, r -> fst != null && snd != null && r.getOrNull() == 1 },
        )
    }

    @Test
    fun testCreateWithConstructor() {
        checkDiscoveredProperties(
            ObjectWithPrimitivesExample::createWithConstructor,
            eq(1),
            { _, x, y, r -> r != null && r.x == x + 1 && r.y == y + 2 && r.weight == 3.3 }
        )
    }

    @Test
    fun testCreateWithSuperConstructor() {
        checkDiscoveredProperties(
            ObjectWithPrimitivesExample::createWithSuperConstructor,
            eq(1),
            { _, x, y, anotherX, r ->
                r != null && r.x == x + 1 && r.y == y + 2 && r.weight == 3.3 && r.anotherX == anotherX + 4
            }
        )
    }

    @Test
    fun testFieldWithDefaultValue() {
        checkDiscoveredProperties(
            ObjectWithPrimitivesExample::fieldWithDefaultValue,
            eq(1),
            { _, x, y, r -> r != null && r.x == x && r.y == y && r.weight == 3.3 && r.valueByDefault == 5 }
        )
    }

    @Test
    fun testValueByDefault() {
        checkDiscoveredProperties(
            ObjectWithPrimitivesExample::valueByDefault,
            eq(1),
            { _, r -> r == 5 }
        )
    }
}
