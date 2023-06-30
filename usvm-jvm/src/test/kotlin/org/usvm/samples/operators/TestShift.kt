package org.usvm.samples.operators

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ge

class TestShift : JavaMethodTestRunner() {
    @Test
    fun `Test left shift int`() {
        checkDiscoveredProperties(
            Shift::shlInt,
            ge(2),
            { _, x, shift, r -> r == 0 && ((x shl shift) != x) },
            { _, x, shift, r -> r == 1 && ((x shl shift) == x) },
        )
    }

    @Disabled("Incorrect primitive cast")
    @Test
    fun `Test left shift byte`() {
        checkDiscoveredProperties(
            Shift::shlByte,
            ge(2),
            { _, x, shift, r -> r == 0 && ((x.toInt() shl shift.toInt()) != x.toInt()) },
            { _, x, shift, r -> r == 1 && ((x.toInt() shl shift.toInt()) == x.toInt()) },
        )
    }

    @Disabled("Incorrect primitive cast")
    @Test
    fun `Test left shift short`() {
        checkDiscoveredProperties(
            Shift::shlShort,
            ge(2),
            { _, x, shift, r -> r == 0 && ((x.toInt() shl shift.toInt()) != x.toInt()) },
            { _, x, shift, r -> r == 1 && ((x.toInt() shl shift.toInt()) == x.toInt()) },
        )
    }

    @Test
    fun `Test left shift long`() {
        checkDiscoveredProperties(
            Shift::shlLong,
            ge(2),
            { _, x, shift, r -> r == 0 && ((x shl shift.toInt()) != x) },
            { _, x, shift, r -> r == 1 && ((x shl shift.toInt()) == x) },
        )
    }

    @Test
    fun `Test left shift long with int shift`() {
        checkDiscoveredProperties(
            Shift::shlLongByInt,
            ge(2),
            { _, x, shift, r -> r == 0 && ((x shl shift) != x) },
            { _, x, shift, r -> r == 1 && ((x shl shift) == x) },
        )
    }

    @Disabled("Incorrect primitive cast")
    @Test
    fun `Test left shift byte with int shift`() {
        checkDiscoveredProperties(
            Shift::shlByteByInt,
            ge(2),
            { _, x, shift, r -> r == 0 && ((x.toInt() shl shift) != x.toInt()) },
            { _, x, shift, r -> r == 1 && ((x.toInt() shl shift) == x.toInt()) },
        )
    }

    @Test
    fun `Test right shift int`() {
        checkDiscoveredProperties(
            Shift::shrInt,
            ge(2),
            { _, x, shift, r -> r == 0 && ((x shr shift) != x) },
            { _, x, shift, r -> r == 1 && ((x shr shift) == x) },
        )
    }

    @Disabled("Incorrect primitive cast")
    @Test
    fun `Test right shift byte`() {
        checkDiscoveredProperties(
            Shift::shrByte,
            ge(2),
            { _, x, shift, r -> r == 0 && ((x.toInt() shr shift.toInt()) != x.toInt()) },
            { _, x, shift, r -> r == 1 && ((x.toInt() shr shift.toInt()) == x.toInt()) },
        )
    }

    @Disabled("Incorrect primitive cast")
    @Test
    fun `Test right shift short`() {
        checkDiscoveredProperties(
            Shift::shrShort,
            ge(2),
            { _, x, shift, r -> r == 0 && ((x.toInt() shr shift.toInt()) != x.toInt()) },
            { _, x, shift, r -> r == 1 && ((x.toInt() shr shift.toInt()) == x.toInt()) },
        )
    }

    @Test
    fun `Test right shift long`() {
        checkDiscoveredProperties(
            Shift::shrLong,
            ge(2),
            { _, x, shift, r -> r == 0 && ((x shr shift.toInt()) != x) },
            { _, x, shift, r -> r == 1 && ((x shr shift.toInt()) == x) },
        )
    }

    @Test
    fun `Test right shift long with int shift`() {
        checkDiscoveredProperties(
            Shift::shrLongByInt,
            ge(2),
            { _, x, shift, r -> r == 0 && ((x shr shift) != x) },
            { _, x, shift, r -> r == 1 && ((x shr shift) == x) },
        )
    }

    @Disabled("Incorrect primitive cast")
    @Test
    fun `Test right shift byte with int shift`() {
        checkDiscoveredProperties(
            Shift::shrByteByInt,
            ge(2),
            { _, x, shift, r -> r == 0 && ((x.toInt() shr shift) != x.toInt()) },
            { _, x, shift, r -> r == 1 && ((x.toInt() shr shift) == x.toInt()) },
        )
    }

    @Test
    fun `Test unsigned right shift int`() {
        checkDiscoveredProperties(
            Shift::ushrInt,
            ge(2),
            { _, x, shift, r -> r == 0 && ((x ushr shift) != x) },
            { _, x, shift, r -> r == 1 && ((x ushr shift) == x) },
        )
    }

    @Disabled("Incorrect primitive cast")
    @Test
    fun `Test unsigned right shift byte`() {
        checkDiscoveredProperties(
            Shift::ushrByte,
            ge(2),
            { _, x, shift, r -> r == 0 && ((x.toInt() ushr shift.toInt()) != x.toInt()) },
            { _, x, shift, r -> r == 1 && ((x.toInt() ushr shift.toInt()) == x.toInt()) },
        )
    }

    @Disabled("Incorrect primitive cast")
    @Test
    fun `Test unsigned right shift short`() {
        checkDiscoveredProperties(
            Shift::ushrShort,
            ge(2),
            { _, x, shift, r -> r == 0 && ((x.toInt() ushr shift.toInt()) != x.toInt()) },
            { _, x, shift, r -> r == 1 && ((x.toInt() ushr shift.toInt()) == x.toInt()) },
        )
    }

    @Test
    fun `Test unsigned right shift long`() {
        checkDiscoveredProperties(
            Shift::ushrLong,
            ge(2),
            { _, x, shift, r -> r == 0 && ((x ushr shift.toInt()) != x) },
            { _, x, shift, r -> r == 1 && ((x ushr shift.toInt()) == x) },
        )
    }

    @Test
    fun `Test unsigned right shift long with int shift`() {
        checkDiscoveredProperties(
            Shift::ushrLongByInt,
            ge(2),
            { _, x, shift, r -> r == 0 && ((x ushr shift) != x) },
            { _, x, shift, r -> r == 1 && ((x ushr shift) == x) },
        )
    }

    @Disabled("Incorrect primitive cast")
    @Test
    fun `Test unsigned right shift byte with int shift`() {
        checkDiscoveredProperties(
            Shift::ushrByteByInt,
            ge(2),
            { _, x, shift, r -> r == 0 && ((x.toInt() ushr shift) != x.toInt()) },
            { _, x, shift, r -> r == 1 && ((x.toInt() ushr shift) == x.toInt()) },
        )
    }
}
