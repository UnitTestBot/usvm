package org.usvm.samples.arrays

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.util.isException

class ArrayStoreExceptionExamplesTest : JavaMethodTestRunner() {
    @Test
    fun testCorrectAssignmentSamePrimitiveType() {
        checkWithExceptionExecutionMatches(
            ArrayStoreExceptionExamples::correctAssignmentSamePrimitiveType,
            eq(3),
            { _, data, result -> result.isSuccess && result.getOrNull() == data?.isNotEmpty() }
        )
    }

    @Test
    fun testCorrectAssignmentIntToIntegerArray() {
        checkWithExceptionExecutionMatches(
            ArrayStoreExceptionExamples::correctAssignmentIntToIntegerArray,
            eq(3),
            { _, data, result -> result.isSuccess && result.getOrNull() == data?.isNotEmpty() }
        )
    }

    @Test
    fun testCorrectAssignmentSubtype() {
        checkWithExceptionExecutionMatches(
            ArrayStoreExceptionExamples::correctAssignmentSubtype,
            eq(3),
            { _, data, result -> result.isSuccess && result.getOrNull() == data?.isNotEmpty() }
        )
    }

    @Test
    fun testCorrectAssignmentToObjectArray() {
        checkWithExceptionExecutionMatches(
            ArrayStoreExceptionExamples::correctAssignmentToObjectArray,
            eq(3),
            { _, data, result -> result.isSuccess && result.getOrNull() == data?.isNotEmpty() }
        )
    }

    @Test
    fun testWrongAssignmentUnrelatedType() {
        checkWithExceptionExecutionMatches(
            ArrayStoreExceptionExamples::wrongAssignmentUnrelatedType,
            eq(3),
            { _, data, result -> data == null && result.isSuccess },
            { _, data, result -> data.isEmpty() && result.isSuccess },
            { _, data, result -> data.isNotEmpty() && result.isException<ArrayStoreException>() },
        )
    }

    @Test
    fun testCheckGenericAssignmentWithCorrectCast() {
        checkWithExceptionExecutionMatches(
            ArrayStoreExceptionExamples::checkGenericAssignmentWithCorrectCast,
            eq(1),
            { _, result -> result.isSuccess }
        )
    }

    @Test
    fun testCheckGenericAssignmentWithWrongCast() {
        checkWithExceptionExecutionMatches(
            ArrayStoreExceptionExamples::checkGenericAssignmentWithWrongCast,
            eq(1),
            { _, result -> result.isException<ArrayStoreException>() },
        )
    }

    @Test
    fun testCheckGenericAssignmentWithExtendsSubtype() {
        checkWithExceptionExecutionMatches(
            ArrayStoreExceptionExamples::checkGenericAssignmentWithExtendsSubtype,
            eq(1),
            { _, result -> result.isSuccess }
        )
    }

    @Test
    fun testCheckGenericAssignmentWithExtendsUnrelated() {
        checkWithExceptionExecutionMatches(
            ArrayStoreExceptionExamples::checkGenericAssignmentWithExtendsUnrelated,
            eq(1),
            { _, result -> result.isException<ArrayStoreException>() },
        )
    }

    @Test
    fun testCheckObjectAssignment() {
        checkWithExceptionExecutionMatches(
            ArrayStoreExceptionExamples::checkObjectAssignment,
            eq(1),
            { _, result -> result.isSuccess }
        )
    }

    // Should this be allowed at all?
    @Test
    fun testCheckWrongAssignmentOfItself() {
        checkWithExceptionExecutionMatches(
            ArrayStoreExceptionExamples::checkWrongAssignmentOfItself,
            eq(1),
            { _, result -> result.isException<ArrayStoreException>() },
        )
    }

    @Test
    fun testCheckGoodAssignmentOfItself() {
        checkWithExceptionExecutionMatches(
            ArrayStoreExceptionExamples::checkGoodAssignmentOfItself,
            eq(1),
            { _, result -> result.isSuccess }
        )
    }

    @Test
    fun testCheckAssignmentToObjectArray() {
        checkWithExceptionExecutionMatches(
            ArrayStoreExceptionExamples::checkAssignmentToObjectArray,
            eq(1),
            { _, result -> result.isSuccess }
        )
    }

    @Test
    fun testArrayCopyForIncompatiblePrimitiveTypes() {
        checkWithExceptionExecutionMatches(
            ArrayStoreExceptionExamples::arrayCopyForIncompatiblePrimitiveTypes,
            eq(3),
            { _, data, result -> data == null && result.isSuccess && result.getOrNull() == null },
            { _, data, result -> data != null && data.isEmpty() && result.isSuccess && result.getOrNull()?.size == 0 },
            { _, data, result -> data != null && data.isNotEmpty() && result.isException<ArrayStoreException>() }
        )
    }

    @Test
    fun testFill2DPrimitiveArray() {
        checkWithExceptionExecutionMatches(
            ArrayStoreExceptionExamples::fill2DPrimitiveArray,
            eq(1),
            { _, result -> result.isSuccess }
        )
    }

    @Test
    fun testFillObjectArrayWithList() {
        checkExecutionMatches(
            ArrayStoreExceptionExamples::fillObjectArrayWithList,
            eq(2),
            { _, list, result -> list != null && result != null && result[0] != null },
            { _, list, result -> list == null && result == null }
        )
    }

    @Test
    fun testFillWithTreeSet() {
        checkExecutionMatches(
            ArrayStoreExceptionExamples::fillWithTreeSet,
            eq(2),
            { _, treeSet, result -> treeSet != null && result != null && result[0] != null },
            { _, treeSet, result -> treeSet == null && result == null }
        )
    }

    @Test
    fun testFillSomeInterfaceArrayWithSomeInterface() {
        checkExecutionMatches(
            ArrayStoreExceptionExamples::fillSomeInterfaceArrayWithSomeInterface,
            eq(2),
            { _, impl, result -> impl == null && result == null },
            { _, impl, result -> impl != null && result != null && result[0] != null }
        )
    }

    @Test
    @Disabled("TODO: Not null path is not found, need to investigate")
    fun testFillObjectArrayWithSomeInterface() {
        checkExecutionMatches(
            ArrayStoreExceptionExamples::fillObjectArrayWithSomeInterface,
            eq(2),
            { _, impl, result -> impl == null && result == null },
            { _, impl, result -> impl != null && result != null && result[0] != null }
        )
    }

    @Test
    fun testFillWithSomeImplementation() {
        checkExecutionMatches(
            ArrayStoreExceptionExamples::fillWithSomeImplementation,
            eq(2),
            { _, impl, result -> impl == null && result == null },
            { _, impl, result -> impl != null && result != null && result[0] != null }
        )
    }
}
