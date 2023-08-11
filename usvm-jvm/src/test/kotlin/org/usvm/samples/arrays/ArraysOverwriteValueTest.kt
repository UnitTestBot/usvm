package org.usvm.samples.arrays

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq

class ArraysOverwriteValueTest : JavaMethodTestRunner() {
    @Test
    fun testByteArray() {
        checkThisAndParamsMutations(
            ArraysOverwriteValue::byteArray,
            eq(4),
            { _, before, _, _, _ -> before == null },
            { _, before, _, _, r -> before != null && before.isEmpty() && r == 1.toByte() },
            { _, before, _, _, r -> before != null && before.isNotEmpty() && before[0] != 0.toByte() && r == 2.toByte() },
            { _, before, _, after, r ->
                val precondition = before != null && before.isNotEmpty() && before[0] == 0.toByte()
                val postcondition = after[0] == 1.toByte() && r == 3.toByte()

                precondition && postcondition
            },
            checkMode = CheckMode.MATCH_PROPERTIES
        )
    }

    @Test
    fun testShortArray() {
        checkThisAndParamsMutations(
            ArraysOverwriteValue::shortArray,
            eq(4),
            { _, before, _, _, _ -> before == null },
            { _, before, _, _, r -> before != null && before.isEmpty() && r == 1.toByte() },
            { _, before, _, _, r -> before != null && before.isNotEmpty() && before[0] != 0.toShort() && r == 2.toByte() },
            { _, before, _, after, r ->
                val precondition = before != null && before.isNotEmpty() && before[0] == 0.toShort()
                val postcondition = after[0] == 1.toShort() && r == 3.toByte()

                precondition && postcondition
            },
            checkMode = CheckMode.MATCH_PROPERTIES
        )
    }

    @Test
    fun testCharArray() {
        checkThisAndParamsMutations(
            ArraysOverwriteValue::charArray,
            eq(4),
            { _, before, _, _, _ -> before == null },
            { _, before, _, _, r -> before != null && before.isEmpty() && r == 1.toChar() },
            { _, before, _, _, r -> before != null && before.isNotEmpty() && before[0] != 0.toChar() && r == 2.toChar() },
            { _, before, _, after, r ->
                val precondition = before != null && before.isNotEmpty() && before[0] == 0.toChar()
                val postcondition = after[0] == 1.toChar() && r == 3.toChar()

                precondition && postcondition
            },
            checkMode = CheckMode.MATCH_PROPERTIES
        )
    }

    @Test
    fun testIntArray() {
        checkThisAndParamsMutations(
            ArraysOverwriteValue::intArray,
            eq(4),
            { _, before, _, _, _ -> before == null },
            { _, before, _, _, r -> before != null && before.isEmpty() && r == 1.toByte() },
            { _, before, _, _, r -> before != null && before.isNotEmpty() && before[0] != 0 && r == 2.toByte() },
            { _, before, _, after, r ->
                before != null && before.isNotEmpty() && before[0] == 0 && after[0] == 1 && r == 3.toByte()
            },
            checkMode = CheckMode.MATCH_PROPERTIES
        )
    }

    @Test
    fun testLongArray() {
        checkThisAndParamsMutations(
            ArraysOverwriteValue::longArray,
            eq(4),
            { _, before, _, _, _ -> before == null },
            { _, before, _, _, r -> before != null && before.isEmpty() && r == 1.toLong() },
            { _, before, _, _, r -> before != null && before.isNotEmpty() && before[0] != 0.toLong() && r == 2.toLong() },
            { _, before, _, after, r ->
                val precondition = before != null && before.isNotEmpty() && before[0] == 0.toLong()
                val postcondition = after[0] == 1.toLong() && r == 3.toLong()

                precondition && postcondition
            },
            checkMode = CheckMode.MATCH_PROPERTIES
        )
    }

    @Test
    fun testFloatArray() {
        checkThisAndParamsMutations(
            ArraysOverwriteValue::floatArray,
            eq(4),
            { _, before, _, _, _ -> before == null },
            { _, before, _, _, r -> before != null && before.isEmpty() && r == 1.0f },
            { _, before, _, _, r -> before != null && before.isNotEmpty() && !before[0].isNaN() && r == 2.0f },
            { _, before, _, after, r ->
                before != null && before.isNotEmpty() && before[0].isNaN() && after[0] == 1.0f && r == 3.0f
            },
            checkMode = CheckMode.MATCH_PROPERTIES
        )
    }

    @Test
    fun testDoubleArray() {
        checkThisAndParamsMutations(
            ArraysOverwriteValue::doubleArray,
            eq(4),
            { _, before, _, _, _ -> before == null },
            { _, before, _, _, r -> before != null && before.isEmpty() && r == 1.00 },
            { _, before, _, _, r -> before != null && before.isNotEmpty() && !before[0].isNaN() && r == 2.0 },
            { _, before, _, after, r ->
                before != null && before.isNotEmpty() && before[0].isNaN() && after[0] == 1.toDouble() && r == 3.0
            },
            checkMode = CheckMode.MATCH_PROPERTIES
        )
    }

    @Test
    fun testBooleanArray() {
        checkThisAndParamsMutations(
            ArraysOverwriteValue::booleanArray,
            eq(4),
            { _, before, _, _, _ -> before == null },
            { _, before, _, _, r -> before != null && before.isEmpty() && r == 1 },
            { _, before, _, _, r -> before != null && before.isNotEmpty() && before[0] && r == 2 },
            { _, before, _, after, r -> before != null && before.isNotEmpty() && !before[0] && after[0] && r == 3 },
            checkMode = CheckMode.MATCH_PROPERTIES
        )
    }

    @Test
    fun testObjectArray() {
        checkThisAndParamsMutations(
            ArraysOverwriteValue::objectArray,
            eq(4),
            { _, before, _, _, _ -> before == null },
            { _, before, _, _, r -> before != null && before.isEmpty() && r == 1 },
            { _, before, _, _, r -> before != null && before.isNotEmpty() && before[0] == null && r == 2 },
            { _, before, _, after, r ->
                before != null && before.isNotEmpty() && before[0] != null && after[0] == null && r == 3
            },
            checkMode = CheckMode.MATCH_PROPERTIES
        )
    }
}