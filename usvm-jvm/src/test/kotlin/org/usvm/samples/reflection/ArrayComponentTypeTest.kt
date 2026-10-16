package org.usvm.samples.reflection

import org.junit.jupiter.api.Test
import org.usvm.machine.JcMachine
import org.usvm.machine.state.JcMethodResult
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.util.getJcMethodByName
import kotlin.test.assertTrue

internal class ArrayComponentTypeTest : JavaMethodTestRunner() {
    init {
        options = options.copy(throwExceptionOnStepFailure = true)
    }

    @Test
    fun testInputClassDoesNotRequireConcreteMetadata() {
        val method = cp.getJcMethodByName(ArrayComponentType::inputClass).method

        val states = JcMachine(cp, options).use { machine ->
            machine.analyze(method)
        }

        assertTrue(states.any { it.methodResult is JcMethodResult.Success })
    }

    @Test
    fun testInputArrayComponentDoesNotRequireConcreteMetadata() {
        val method = cp.getJcMethodByName(ArrayComponentType::inputArray).method

        val states = JcMachine(cp, options).use { machine ->
            machine.analyze(method)
        }

        assertTrue(states.any { it.methodResult is JcMethodResult.Success })
    }

    @Test
    fun testNullArrayComponentThrowsNullPointerException() {
        checkDiscoveredPropertiesWithExceptions(
            ArrayComponentType::nullComponentArray,
            eq(1),
            { _, result -> result.exceptionOrNull() is NullPointerException },
        )
    }

    @Test
    fun testPrimitiveArrayComponent() {
        checkDiscoveredProperties(
            ArrayComponentType::primitiveArray,
            eq(1),
            { _, result -> result == Boolean::class.javaPrimitiveType },
        )
    }

    @Test
    fun testReferenceArrayComponent() {
        checkDiscoveredProperties(
            ArrayComponentType::referenceArray,
            eq(1),
            { _, result -> result == Any::class.java },
        )
    }

    @Test
    fun testNestedArrayComponent() {
        checkDiscoveredProperties(
            ArrayComponentType::nestedArray,
            eq(1),
            { _, result -> result == IntArray::class.java },
        )
    }

    @Test
    fun testReferenceClassHasNoComponent() {
        checkDiscoveredProperties(
            ArrayComponentType::referenceClass,
            eq(1),
            { _, result -> result == null },
        )
    }

    @Test
    fun testPrimitiveClassHasNoComponent() {
        checkDiscoveredProperties(
            ArrayComponentType::primitiveClass,
            eq(1),
            { _, result -> result == null },
        )
    }

    @Test
    fun testVoidClassHasNoComponent() {
        checkDiscoveredProperties(
            ArrayComponentType::voidClass,
            eq(1),
            { _, result -> result == null },
        )
    }

    @Test
    fun testInterfaceClassHasNoComponent() {
        checkDiscoveredProperties(
            ArrayComponentType::interfaceClass,
            eq(1),
            { _, result -> result == null },
        )
    }

    @Test
    fun testComponentTypeCanAllocateAndCopyArray() {
        checkDiscoveredProperties(
            ArrayComponentType::growBooleanArray,
            eq(1),
            { _, result -> result.contentEquals(booleanArrayOf(true, false, false)) },
        )
    }
}
