package org.usvm.samples.objects

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.isException


internal class ObjectWithRefFieldsExampleTest : JavaMethodTestRunner() {
    @Test
    fun testDefaultValue() {
        checkDiscoveredProperties(
            ObjectWithRefFieldExample::defaultValue,
            eq(1),
            { _, r -> r != null && r.x == 0 && r.y == 0 && r.weight == 0.0 && r.arrayField == null && r.refField == null },
        )
    }

    @Test
    @Disabled("Some properties were not discovered at positions (from 0): [2]. Fix branch coverage")
    fun testWriteToRefTypeField() {
        checkDiscoveredProperties(
            ObjectWithRefFieldExample::writeToRefTypeField,
            ignoreNumberOfAnalysisResults,
            { _, _, v, _ -> v != 42 },
            { _, o, v, _ -> v == 42 && o == null },
            { _, o, v, _ -> v == 42 && o != null && o.refField != null },
            { _, o, v, r ->
                v == 42 && o != null && o.refField == null && r != null && r.refField.a == v && r.refField.b == 2 * v
            },
        )
    }

    @Test
    fun testDefaultFieldValues() {
        checkDiscoveredProperties(
            ObjectWithRefFieldExample::defaultFieldValues,
            eq(1),
            { _, r ->
                r != null && r.x == 0 && r.y == 0 && r.weight == 0.0 && r.refField == null && r.arrayField == null
            }
        )
    }

    @Test
    fun testReadFromRefTypeField() {
        checkDiscoveredProperties(
            ObjectWithRefFieldExample::readFromRefTypeField,
            ignoreNumberOfAnalysisResults,
            { _, o, _ -> o == null },
            { _, o, _ -> o != null && o.refField == null },
            { _, o, r -> o?.refField != null && o.refField.a <= 0 && r == -1 },
            { _, o, r -> o?.refField != null && o.refField.a > 0 && o.refField.a == r }
        )
    }

    @Test
    fun testWriteToArrayField() {
        checkDiscoveredProperties(
            ObjectWithRefFieldExample::writeToArrayField,
            eq(3),
            { _, _, length, _ -> length < 3 },
            { _, o, length, _ -> length >= 3 && o == null },
            { _, o, length, r ->
                require(r != null)

                val array = r.arrayField

                val preconditions = length >= 3 && o != null
                val contentConstraint = array.dropLast(1) == (1 until length).toList() && array.last() == 100

                preconditions && contentConstraint
            },
        )
    }

    @Test
    fun testReadFromArrayField() {
        checkDiscoveredProperties(
            ObjectWithRefFieldExample::readFromArrayField,
            ignoreNumberOfAnalysisResults,
            { _, o, _, _ -> o == null },
            { _, o, _, _ -> o != null && o.arrayField == null },
            { _, o, _, _ -> o?.arrayField != null && o.arrayField.size < 3 },
            { _, o, v, r -> o?.arrayField != null && o.arrayField.size >= 3 && o.arrayField[2] == v && r == 1 },
            { _, o, v, r -> o?.arrayField != null && o.arrayField.size >= 3 && o.arrayField[2] != v && r == 2 }
        )
    }

    fun testCompareTwoDifferentObjectsFromArguments() {
        checkDiscoveredProperties(
            ObjectWithRefFieldExample::compareTwoDifferentObjectsFromArguments,
            ignoreNumberOfAnalysisResults,
            { _, fst, _, _ -> fst == null },
            { _, fst, snd, _ -> fst != null && fst.x > 0 && snd == null },
            { _, fst, snd, _ -> fst != null && fst.x <= 0 && snd == null },
            { _, fst, snd, r -> fst != null && snd != null && fst.x > 0 && snd.x < 0 && r == 1 },
            { _, fst, snd, r -> fst != null && snd != null && ((fst.x > 0 && snd.x >= 0) || fst.x <= 0) && fst === snd && r == 2 },
            { _, fst, snd, r -> fst != null && snd != null && (fst.x <= 0 || (fst.x > 0 && snd.x >= 0)) && fst !== snd && r == 3 }
        )
    }

    @Test
    fun testCompareTwoObjectsWithNullRefField() {
        checkDiscoveredPropertiesWithExceptions(
            ObjectWithRefFieldExample::compareTwoObjectsWithNullRefField,
            eq(4),
            { _, fst, _, r -> fst == null && r.isException<NullPointerException>() },
            { _, fst, snd, r -> fst != null && snd == null && r.isException<NullPointerException>() },
            { _, fst, snd, r -> fst != null && snd != null && r.getOrNull() == 1 /* && fst == snd by ref */ },
            { _, fst, snd, r -> fst != null && snd != null && r.getOrNull() == 2 /* && fst != snd by ref */ },
        )
    }

    @Test
    fun testCompareTwoObjectsWithDifferentRefField() {
        checkDiscoveredPropertiesWithExceptions(
            ObjectWithRefFieldExample::compareTwoObjectsWithDifferentRefField,
            ignoreNumberOfAnalysisResults,
            { _, fst, _, _, r -> fst == null && r.isException<NullPointerException>() },
            { _, fst, snd, _, r -> fst != null && snd == null && r.isException<NullPointerException>() },
            { _, fst, snd, _, r -> fst != null && snd != null && r.getOrNull() == 1 /* fst == snd by ref */ },
            { _, fst, snd, _, r -> fst != null && snd != null && r.getOrNull() == 2 /* fst != snd by ref */ },
        )
    }

    @Test
    fun testCompareTwoObjectsWithTheDifferentRefField() {
        checkDiscoveredPropertiesWithExceptions(
            ObjectWithRefFieldExample::compareTwoObjectsWithTheDifferentRefField,
            ignoreNumberOfAnalysisResults,
            { _, fst, _, r -> fst == null && r.isException<NullPointerException>() },
            { _, fst, snd, r -> fst != null && snd == null && r.isException<NullPointerException>() },
            { _, fst, snd, r -> fst != null && snd != null && fst.refField === snd.refField && r.getOrNull() == true },
            { _, fst, snd, r -> fst != null && snd != null && fst.refField !== snd.refField && r.getOrNull() == false }
        )
    }

    @Test
    fun testCompareTwoObjectsWithTheSameRefField() {
        checkDiscoveredPropertiesWithExceptions(
            ObjectWithRefFieldExample::compareTwoObjectsWithTheSameRefField,
            ignoreNumberOfAnalysisResults,
            { _, fst, _, r -> fst == null && r.isException<NullPointerException>() },
            { _, fst, snd, r -> fst != null && snd == null && r.isException<NullPointerException>() },
            { _, fst, snd, r -> fst != null && snd != null && r.getOrNull() == 1 /* && fst == snd by ref */ },
            { _, fst, snd, r -> fst != null && snd != null && r.getOrNull() == 2 /* && fst != snd by ref */ },
        )
    }
}
