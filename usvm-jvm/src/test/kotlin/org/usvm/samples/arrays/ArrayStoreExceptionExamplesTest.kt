package org.usvm.samples.arrays

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.util.isException

class ArrayStoreExceptionExamplesTest : JavaMethodTestRunner() {
    @Test
    @Disabled("Some types don't match at positions (from 0): [1]. ")
    fun testCorrectAssignmentSamePrimitiveType() {
        checkDiscoveredPropertiesWithExceptions(
            ArrayStoreExceptionExamples::correctAssignmentSamePrimitiveType,
            eq(3),
            { _, data, result -> result.isSuccess && result.getOrNull() == data?.isNotEmpty() }
        )
    }

    @Test
    @Disabled("Unexpected lvalue org.usvm.machine.JcStaticFieldRef@46468f0")
    fun testCorrectAssignmentIntToIntegerArray() {
        checkDiscoveredPropertiesWithExceptions(
            ArrayStoreExceptionExamples::correctAssignmentIntToIntegerArray,
            eq(3),
            { _, data, result -> result.isSuccess && result.getOrNull() == data?.isNotEmpty() }
        )
    }

    @Test
    @Disabled("Unexpected lvalue org.usvm.machine.JcStaticFieldRef@7ddeb27f")
    fun testCorrectAssignmentSubtype() {
        checkDiscoveredPropertiesWithExceptions(
            ArrayStoreExceptionExamples::correctAssignmentSubtype,
            eq(3),
            { _, data, result -> result.isSuccess && result.getOrNull() == data?.isNotEmpty() }
        )
    }

    @Test
    @Disabled("Unexpected lvalue org.usvm.machine.JcStaticFieldRef@3684d2c0")
    fun testCorrectAssignmentToObjectArray() {
        checkDiscoveredPropertiesWithExceptions(
            ArrayStoreExceptionExamples::correctAssignmentToObjectArray,
            eq(3),
            { _, data, result -> result.isSuccess && result.getOrNull() == data?.isNotEmpty() }
        )
    }

    @Test
    @Disabled("An operation is not implemented.")
    fun testWrongAssignmentUnrelatedType() {
        checkDiscoveredPropertiesWithExceptions(
            ArrayStoreExceptionExamples::wrongAssignmentUnrelatedType,
            eq(3),
            { _, data, result -> data == null && result.isSuccess },
            { _, data, result -> data.isEmpty() && result.isSuccess },
            { _, data, result -> data.isNotEmpty() && result.isException<ArrayStoreException>() },
        )
    }

    @Test
    @Disabled("Unexpected lvalue org.usvm.machine.JcStaticFieldRef@17176b18")
    fun testCheckGenericAssignmentWithCorrectCast() {
        checkDiscoveredPropertiesWithExceptions(
            ArrayStoreExceptionExamples::checkGenericAssignmentWithCorrectCast,
            eq(1),
            { _, result -> result.isSuccess }
        )
    }

    @Test
    @Disabled("An operation is not implemented: Not yet implemented")
    fun testCheckGenericAssignmentWithWrongCast() {
        checkDiscoveredPropertiesWithExceptions(
            ArrayStoreExceptionExamples::checkGenericAssignmentWithWrongCast,
            eq(1),
            { _, result -> result.isException<ArrayStoreException>() },
        )
    }

    @Test
    @Disabled("Unexpected lvalue org.usvm.machine.JcStaticFieldRef@3c6fb501")
    fun testCheckGenericAssignmentWithExtendsSubtype() {
        checkDiscoveredPropertiesWithExceptions(
            ArrayStoreExceptionExamples::checkGenericAssignmentWithExtendsSubtype,
            eq(1),
            { _, result -> result.isSuccess }
        )
    }

    @Test
    @Disabled("An operation is not implemented: Not yet implemented")
    fun testCheckGenericAssignmentWithExtendsUnrelated() {
        checkDiscoveredPropertiesWithExceptions(
            ArrayStoreExceptionExamples::checkGenericAssignmentWithExtendsUnrelated,
            eq(1),
            { _, result -> result.isException<ArrayStoreException>() },
        )
    }

    @Test
    @Disabled("An operation is not implemented: Not yet implemented")
    fun testCheckObjectAssignment() {
        checkDiscoveredPropertiesWithExceptions(
            ArrayStoreExceptionExamples::checkObjectAssignment,
            eq(1),
            { _, result -> result.isSuccess }
        )
    }

    @Test
    @Disabled("Some properties were not discovered at positions (from 0): [0]")
    fun testCheckWrongAssignmentOfItself() {
        checkDiscoveredPropertiesWithExceptions(
            ArrayStoreExceptionExamples::checkWrongAssignmentOfItself,
            eq(1),
            { _, result -> result.isException<ArrayStoreException>() },
        )
    }

    @Test
    @Disabled("Not implemented: reference cast")
    fun testCheckGoodAssignmentOfItself() {
        checkDiscoveredPropertiesWithExceptions(
            ArrayStoreExceptionExamples::checkGoodAssignmentOfItself,
            eq(1),
            { _, result -> result.isSuccess }
        )
    }

    @Test
    @Disabled("Unexpected lvalue org.usvm.machine.JcStaticFieldRef@44fd7ba4")
    fun testCheckAssignmentToObjectArray() {
        checkDiscoveredPropertiesWithExceptions(
            ArrayStoreExceptionExamples::checkAssignmentToObjectArray,
            eq(1),
            { _, result -> result.isSuccess }
        )
    }

    @Test
    @Disabled("Sequence is empty.")
    fun testArrayCopyForIncompatiblePrimitiveTypes() {
        checkDiscoveredPropertiesWithExceptions(
            ArrayStoreExceptionExamples::arrayCopyForIncompatiblePrimitiveTypes,
            eq(3),
            { _, data, result -> data == null && result.isSuccess && result.getOrNull() == null },
            { _, data, result -> data != null && data.isEmpty() && result.isSuccess && result.getOrNull()?.size == 0 },
            { _, data, result -> data != null && data.isNotEmpty() && result.isException<ArrayStoreException>() }
        )
    }

    @Test
    @Disabled("Expected exactly 1 executions, but 2 found")
    fun testFill2DPrimitiveArray() {
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
    @Disabled("ClassNotFound java.util.List<java.lang.Integer>")
    fun testFillObjectArrayWithList() {
        checkDiscoveredProperties(
            ArrayStoreExceptionExamples::fillObjectArrayWithList,
            eq(2),
            { _, list, result -> list != null && result != null && result[0] != null },
            { _, list, result -> list == null && result == null }
        )
    }

    @Test
    @Disabled("Class not found java.util.TreeSet<java.lang.Integer>")
    fun testFillWithTreeSet() {
        checkDiscoveredProperties(
            ArrayStoreExceptionExamples::fillWithTreeSet,
            eq(2),
            { _, treeSet, result -> treeSet != null && result != null && result[0] != null },
            { _, treeSet, result -> treeSet == null && result == null }
        )
    }

    @Test
    @Disabled("java.lang.InstantiationException: org.usvm.samples.arrays.SomeInterface")
    fun testFillSomeInterfaceArrayWithSomeInterface() {
        checkDiscoveredProperties(
            ArrayStoreExceptionExamples::fillSomeInterfaceArrayWithSomeInterface,
            eq(2),
            { _, impl, result -> impl == null && result == null },
            { _, impl, result -> impl != null && result != null && result[0] != null }
        )
    }

    @Test
    @Disabled("java.lang.InstantiationException: org.usvm.samples.arrays.SomeInterface")
    fun testFillObjectArrayWithSomeInterface() {
        checkDiscoveredProperties(
            ArrayStoreExceptionExamples::fillObjectArrayWithSomeInterface,
            eq(2),
            { _, impl, result -> impl == null && result == null },
            { _, impl, result -> impl != null && result != null && result[0] != null }
        )
    }

    @Test
    @Disabled("An operation is not implemented: Not yet implemented")
    fun testFillWithSomeImplementation() {
        checkDiscoveredProperties(
            ArrayStoreExceptionExamples::fillWithSomeImplementation,
            eq(2),
            { _, impl, result -> impl == null && result == null },
            { _, impl, result -> impl != null && result != null && result[0] != null }
        )
    }
}
