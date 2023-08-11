package org.usvm.samples.arrays

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.util.disableTest
import org.usvm.util.isException

class ArrayStoreExceptionExamplesTest : JavaMethodTestRunner() {
    @Test
    fun testCorrectAssignmentSamePrimitiveType() {
        checkDiscoveredPropertiesWithExceptions(
            ArrayStoreExceptionExamples::correctAssignmentSamePrimitiveType,
            eq(3),
            { _, data, result -> result.isSuccess && result.getOrNull() == false && data == null },
            { _, data, result -> result.isSuccess && result.getOrNull() == false && data != null && data.isEmpty() },
            { _, data, result -> result.isSuccess && result.getOrNull() == true && data != null && data.isNotEmpty() }
        )
    }

    @Test
    fun testCorrectAssignmentIntToIntegerArray() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredPropertiesWithExceptions(
            ArrayStoreExceptionExamples::correctAssignmentIntToIntegerArray,
            eq(3),
            { _, data, result -> result.isSuccess && result.getOrNull() == data?.isNotEmpty() }
        )
    }

    @Test
    fun testCorrectAssignmentSubtype() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredPropertiesWithExceptions(
            ArrayStoreExceptionExamples::correctAssignmentSubtype,
            eq(3),
            { _, data, result -> result.isSuccess && result.getOrNull() == data?.isNotEmpty() }
        )
    }

    @Test
    fun testCorrectAssignmentToObjectArray() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredPropertiesWithExceptions(
            ArrayStoreExceptionExamples::correctAssignmentToObjectArray,
            eq(3),
            { _, data, result -> result.isSuccess && result.getOrNull() == data?.isNotEmpty() }
        )
    }

    @Test
    fun testWrongAssignmentUnrelatedType() = disableTest("Some properties were not discovered at positions (from 0): [2]") {
        checkDiscoveredPropertiesWithExceptions(
            ArrayStoreExceptionExamples::wrongAssignmentUnrelatedType,
            eq(3),
            { _, data, result -> data == null && result.isSuccess },
            { _, data, result -> data.isEmpty() && result.isSuccess },
            { _, data, result -> data.isNotEmpty() && result.isException<ArrayStoreException>() },
        )
    }

    @Test
    fun testCheckGenericAssignmentWithCorrectCast() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredPropertiesWithExceptions(
            ArrayStoreExceptionExamples::checkGenericAssignmentWithCorrectCast,
            eq(1),
            { _, result -> result.isSuccess }
        )
    }

    @Test
    fun testCheckGenericAssignmentWithWrongCast() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredPropertiesWithExceptions(
            ArrayStoreExceptionExamples::checkGenericAssignmentWithWrongCast,
            eq(1),
            { _, result -> result.isException<ArrayStoreException>() },
        )
    }

    @Test
    fun testCheckGenericAssignmentWithExtendsSubtype() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredPropertiesWithExceptions(
            ArrayStoreExceptionExamples::checkGenericAssignmentWithExtendsSubtype,
            eq(1),
            { _, result -> result.isSuccess }
        )
    }

    @Test
    fun testCheckGenericAssignmentWithExtendsUnrelated() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredPropertiesWithExceptions(
            ArrayStoreExceptionExamples::checkGenericAssignmentWithExtendsUnrelated,
            eq(1),
            { _, result -> result.isException<ArrayStoreException>() },
        )
    }

    @Test
    fun testCheckObjectAssignment() {
        checkDiscoveredPropertiesWithExceptions(
            ArrayStoreExceptionExamples::checkObjectAssignment,
            eq(1),
            { _, result -> result.isSuccess }
        )
    }

    @Test // todo: Support generics
    fun testCheckWrongAssignmentOfItself() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredPropertiesWithExceptions(
            ArrayStoreExceptionExamples::checkWrongAssignmentOfItself,
            eq(1),
            { _, result -> result.isException<ArrayStoreException>() },
        )
    }

    @Test
    fun testCheckGoodAssignmentOfItself() {
        checkDiscoveredPropertiesWithExceptions(
            ArrayStoreExceptionExamples::checkGoodAssignmentOfItself,
            eq(1),
            { _, result -> result.isSuccess }
        )
    }

    @Test
    fun testCheckAssignmentToObjectArray() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredPropertiesWithExceptions(
            ArrayStoreExceptionExamples::checkAssignmentToObjectArray,
            eq(1),
            { _, result -> result.isSuccess }
        )
    }

    @Test
    fun testArrayCopyForIncompatiblePrimitiveTypes() = disableTest("Some properties were not discovered at positions (from 0): [2]") {
        checkDiscoveredPropertiesWithExceptions(
            ArrayStoreExceptionExamples::arrayCopyForIncompatiblePrimitiveTypes,
            eq(3),
            { _, data, result -> data == null && result.isSuccess && result.getOrNull() == null },
            { _, data, result -> data != null && data.isEmpty() && result.isSuccess && result.getOrNull()?.size == 0 },
            { _, data, result -> data != null && data.isNotEmpty() && result.isException<ArrayStoreException>() }
        )
    }

    @Test
    fun testFill2DPrimitiveArray() = disableTest("Some executions violated invariants. Support multidimensional arrays initialization") {
        checkDiscoveredPropertiesWithExceptions(
            ArrayStoreExceptionExamples::fill2DPrimitiveArray,
            eq(1),
            { _, result -> result.isSuccess },
            invariants = arrayOf(
                { _, result -> !result.isFailure }
            )
        )
    }

    @Test
    fun testFillObjectArrayWithList() {
        checkDiscoveredProperties(
            ArrayStoreExceptionExamples::fillObjectArrayWithList,
            eq(2),
            { _, list, result -> list != null && result != null && result[0] != null },
            { _, list, result -> list == null && result == null }
        )
    }

    @Test
    fun testFillWithTreeSet() {
        checkDiscoveredProperties(
            ArrayStoreExceptionExamples::fillWithTreeSet,
            eq(2),
            { _, treeSet, result -> treeSet != null && result != null && result[0] != null },
            { _, treeSet, result -> treeSet == null && result == null }
        )
    }

    @Test
    fun testFillSomeInterfaceArrayWithSomeInterface() {
        checkDiscoveredProperties(
            ArrayStoreExceptionExamples::fillSomeInterfaceArrayWithSomeInterface,
            eq(2),
            { _, impl, result -> impl == null && result == null },
            { _, impl, result -> impl != null && result != null && result[0] != null }
        )
    }

    @Test
    fun testFillObjectArrayWithSomeInterface() {
        checkDiscoveredProperties(
            ArrayStoreExceptionExamples::fillObjectArrayWithSomeInterface,
            eq(2),
            { _, impl, result -> impl == null && result == null },
            { _, impl, result -> impl != null && result != null && result[0] != null }
        )
    }

    @Test
    fun testFillWithSomeImplementation() {
        checkDiscoveredProperties(
            ArrayStoreExceptionExamples::fillWithSomeImplementation,
            eq(2),
            { _, impl, result -> impl == null && result == null },
            { _, impl, result -> impl != null && result != null && result[0] != null }
        )
    }
}
