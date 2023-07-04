package org.usvm.samples.structures

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.between
import org.usvm.test.util.checkers.eq


import kotlin.math.min

internal class MinStackExampleTest : JavaMethodTestRunner() {
    @Test
    fun testCreate() {
        checkDiscoveredProperties(
            MinStackExample::create,
            eq(3),
            { _, initialValues, _ -> initialValues == null },
            { _, initialValues, _ -> initialValues != null && initialValues.size < 3 },
            { _, initialValues, result ->
                require(initialValues != null && result != null)

                val stackExample = MinStackExample().create(initialValues)
                val initialSize = initialValues.size

                val sizesConstraint = initialSize >= 3 && result.size == 4
                val stacksSize = stackExample.stack.take(initialSize) == result.stack.take(initialSize)
                val minStackSize = stackExample.minStack.take(initialSize) == result.minStack.take(initialSize)

                sizesConstraint && stacksSize && minStackSize
            },
        )
    }

    @Test
    fun testAddSingleValue() {
        checkDiscoveredProperties(
            MinStackExample::addSingleValue,
            eq(4),
            { _, initialValues, _ -> initialValues == null },
            { _, initialValues, _ -> initialValues != null && initialValues.isEmpty() },
            { _, initialValues, result ->
                require(initialValues != null && result != null)

                val sizeConstraint = initialValues.isNotEmpty()
                val firstElementConstraint = result.stack.first() == initialValues.first()
                val secondElementConstraint = result.stack[1] == initialValues.first() - 100

                sizeConstraint && firstElementConstraint && secondElementConstraint
            },
        )
    }

    @Test
    fun testGetMinValue() {
        checkDiscoveredProperties(
            MinStackExample::getMinValue,
            eq(3),
            { _, initialValues, _ -> initialValues == null },
            { _, initialValues, result -> initialValues != null && initialValues.isEmpty() && result == -1L },
            { _, initialValues, result ->
                initialValues != null && initialValues.isNotEmpty() && result == min(-1L, initialValues.minOrNull()!!)
            },
        )
    }

    @Test
    @Disabled("An operation is not implemented: Not yet implemented")
    fun testRemoveValue() {
        checkDiscoveredProperties(
            MinStackExample::removeValue,
            eq(4),
            { _, initialValues, _ -> initialValues == null },
            { _, initialValues, _ -> initialValues != null && initialValues.isEmpty() },
            { _, initialValues, result ->
                initialValues != null && initialValues.size == 1 && result != null && result.size == initialValues.size - 1
            },
            { _, initialValues, result ->
                initialValues != null && initialValues.size > 1 && result != null && result.size == initialValues.size - 1
            },
        )
    }

    @Test
    fun testConstruct() {
        checkDiscoveredProperties(
            MinStackExample::construct,
            between(3..4),
            { _, values, _ -> values == null },
            { _, values, result -> values != null && values.isEmpty() && result != null && result.size == 0 },
            { _, values, result ->
                require(values != null && result != null)

                val stackExample = MinStackExample().construct(values)

                val sizeConstraint = values.isNotEmpty() && result.size == values.size
                val stackSize = stackExample.stack.take(values.size) == result.stack.take(values.size)
                val valueConstraint = stackExample.minStack.take(values.size) == result.minStack.take(values.size)

                sizeConstraint && stackSize && valueConstraint
            },
        )
    }
}