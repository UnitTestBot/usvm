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
            { _, data, result -> result.isSuccess && result.getOrNull() == data?.isNotEmpty() }
        )
    }

    @Test
    fun testCorrectAssignmentIntToIntegerArray() {
        checkWithExceptionExecutionMatches(
            ArrayStoreExceptionExamples::correctAssignmentIntToIntegerArray,
            { _, data, result -> result.isSuccess && result.getOrNull() == data?.isNotEmpty() }
        )
    }

    @Test
    fun testCorrectAssignmentSubtype() {
        checkWithExceptionExecutionMatches(
            ArrayStoreExceptionExamples::correctAssignmentSubtype,
            { _, data, result -> result.isSuccess && result.getOrNull() == data?.isNotEmpty() }
        )
    }

    @Test
    fun testCorrectAssignmentToObjectArray() {
        checkWithExceptionExecutionMatches(
            ArrayStoreExceptionExamples::correctAssignmentToObjectArray,
            { _, data, result -> result.isSuccess && result.getOrNull() == data?.isNotEmpty() }
        )
    }

    @Test
    fun testWrongAssignmentUnrelatedType() {
        checkWithExceptionExecutionMatches(
            ArrayStoreExceptionExamples::wrongAssignmentUnrelatedType,
            { _, data, result -> data == null && result.isSuccess },
            { _, data, result -> data.isEmpty() && result.isSuccess },
            { _, data, result -> data.isNotEmpty() && result.isException<ArrayStoreException>() },
        )
    }

    @Test
    fun testCheckGenericAssignmentWithCorrectCast() {
        checkWithExceptionExecutionMatches(
            ArrayStoreExceptionExamples::checkGenericAssignmentWithCorrectCast,
            { _, result -> result.isSuccess }
        )
    }

    @Test
    fun testCheckGenericAssignmentWithWrongCast() {
        checkWithExceptionExecutionMatches(
            ArrayStoreExceptionExamples::checkGenericAssignmentWithWrongCast,
            { _, result -> result.isException<ArrayStoreException>() },
        )
    }

    @Test
    fun testCheckGenericAssignmentWithExtendsSubtype() {
        checkWithExceptionExecutionMatches(
            ArrayStoreExceptionExamples::checkGenericAssignmentWithExtendsSubtype,
            { _, result -> result.isSuccess }
        )
    }

    @Test
    fun testCheckGenericAssignmentWithExtendsUnrelated() {
        checkWithExceptionExecutionMatches(
            ArrayStoreExceptionExamples::checkGenericAssignmentWithExtendsUnrelated,
            { _, result -> result.isException<ArrayStoreException>() },
        )
    }

    @Test
    fun testCheckObjectAssignment() {
        checkWithExceptionExecutionMatches(
            ArrayStoreExceptionExamples::checkObjectAssignment,
            { _, result -> result.isSuccess }
        )
    }

    // Should this be allowed at all?
    @Test
    fun testCheckWrongAssignmentOfItself() {
        checkWithExceptionExecutionMatches(
            ArrayStoreExceptionExamples::checkWrongAssignmentOfItself,
            { _, result -> result.isException<ArrayStoreException>() },
        )
    }

    @Test
    fun testCheckGoodAssignmentOfItself() {
        checkWithExceptionExecutionMatches(
            ArrayStoreExceptionExamples::checkGoodAssignmentOfItself,
            { _, result -> result.isSuccess }
        )
    }

    @Test
    fun testCheckAssignmentToObjectArray() {
        checkWithExceptionExecutionMatches(
            ArrayStoreExceptionExamples::checkAssignmentToObjectArray,
            { _, result -> result.isSuccess }
        )
    }

    @Test
    fun testArrayCopyForIncompatiblePrimitiveTypes() {
        checkWithExceptionExecutionMatches(
            ArrayStoreExceptionExamples::arrayCopyForIncompatiblePrimitiveTypes,
            { _, data, result -> data == null && result.isSuccess && result.getOrNull() == null },
            { _, data, result -> data != null && data.isEmpty() && result.isSuccess && result.getOrNull()?.size == 0 },
            { _, data, result -> data != null && data.isNotEmpty() && result.isException<ArrayStoreException>() }
        )
    }

    @Test
    fun testFill2DPrimitiveArray() {
        checkWithExceptionExecutionMatches(
            ArrayStoreExceptionExamples::fill2DPrimitiveArray,
            { _, result -> result.isSuccess }
        )
    }

    @Test
    fun testFillObjectArrayWithList() {
        checkExecutionMatches(
            ArrayStoreExceptionExamples::fillObjectArrayWithList,
            { _, list, result -> list != null && result != null && result[0] != null },
            { _, list, result -> list == null && result == null }
        )
    }

    @Test
    fun testFillWithTreeSet() {
        checkExecutionMatches(
            ArrayStoreExceptionExamples::fillWithTreeSet,
            { _, treeSet, result -> treeSet != null && result != null && result[0] != null },
            { _, treeSet, result -> treeSet == null && result == null }
        )
    }

    @Test
    fun testFillSomeInterfaceArrayWithSomeInterface() {
        checkExecutionMatches(
            ArrayStoreExceptionExamples::fillSomeInterfaceArrayWithSomeInterface,
            { _, impl, result -> impl == null && result == null },
            { _, impl, result -> impl != null && result != null && result[0] != null }
        )
    }

    @Test
    @Disabled("TODO: Not null path is not found, need to investigate")
    fun testFillObjectArrayWithSomeInterface() {
        checkExecutionMatches(
            ArrayStoreExceptionExamples::fillObjectArrayWithSomeInterface,
            { _, impl, result -> impl == null && result == null },
            { _, impl, result -> impl != null && result != null && result[0] != null }
        )
    }

    @Test
    fun testFillWithSomeImplementation() {
        checkExecutionMatches(
            ArrayStoreExceptionExamples::fillWithSomeImplementation,
            { _, impl, result -> impl == null && result == null },
            { _, impl, result -> impl != null && result != null && result[0] != null }
        )
    }
}
