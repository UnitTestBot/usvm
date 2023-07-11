package org.usvm.types

import io.mockk.every
import io.mockk.spyk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.usvm.types.system.base1
import org.usvm.types.system.base2
import org.usvm.types.system.comparable
import org.usvm.types.system.derived1A
import org.usvm.types.system.derived1B
import org.usvm.types.system.derivedMulti
import org.usvm.types.system.derivedMultiInterfaces
import org.usvm.types.system.interface1
import org.usvm.types.system.interface2
import org.usvm.types.system.testTypeSystem
import org.usvm.types.system.top
import org.usvm.types.system.userComparable
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TypeStreamTest {
    private val typeSystem = testTypeSystem

    @Test
    fun `Test topType`() {
        val typeStream = typeSystem.topTypeStream()
            .filterBySupertype(top)
        val result = typeStream.take(100)
        assertEquals(100, result.size)
        assertTrue(result.all(typeSystem::isInstantiable))
    }


    @Test
    fun `Test comparable`() {
        val typeStream = typeSystem.topTypeStream()
            .filterBySupertype(comparable)
        val result = typeStream.take(100)
        assertEquals(100, result.size)
        assertTrue(result.all(typeSystem::isInstantiable))
    }

    @Test
    fun `Test open type inheritance`() {
        val typeStream = typeSystem.topTypeStream()
            .filterBySupertype(base1)

        typeStream.take100AndAssertEqualsToSetOf(base1, derived1A, derived1B)
    }

    @Test
    fun `Test interface inheritance`() {
        val typeStream = typeSystem.topTypeStream()
            .filterBySupertype(interface1)

        typeStream.take100AndAssertEqualsToSetOf(derived1A, derivedMulti, derivedMultiInterfaces)
    }


    @Test
    fun `Test empty intersection`() {
        val typeStream = typeSystem.topTypeStream()
            .filterBySupertype(base1)
            .filterBySupertype(base2)

        typeStream.take100AndAssertEqualsToSetOf()
    }

    @Test
    fun `Test exact type`() {
        val typeStream = typeSystem.topTypeStream()
            .filterBySupertype(derivedMulti)
            .filterBySubtype(derivedMulti)

        typeStream.take100AndAssertEqualsToSetOf(derivedMulti)
    }

    @Test
    fun `Test supertype & !supertype`() {
        val typeStream = typeSystem.topTypeStream()
            .filterBySupertype(interface1)
            .filterByNotSupertype(interface2)

        typeStream.take100AndAssertEqualsToSetOf(derived1A)
    }

    @Test
    fun `Test comparable & user's interface inheritor`() { // works 100ms
        val typeStream = typeSystem.topTypeStream()
            .filterBySupertype(comparable)
            .filterBySupertype(interface2)

        typeStream.take100AndAssertEqualsToSetOf(userComparable)
    }

    @Test
    fun `Test user's interface & comparable inheritor`() { // works less than 10ms
        val typeStream = typeSystem.topTypeStream()
            .filterBySupertype(interface2)
            .filterBySupertype(comparable)

        typeStream.take100AndAssertEqualsToSetOf(userComparable)
    }

    @Test
    fun `Test subtype1 & subtype2 & subtype3`() {
        val typeStream = typeSystem.topTypeStream()
            .filterBySupertype(base2)
            .filterBySupertype(interface1)
            .filterBySupertype(interface2)

        typeStream.take100AndAssertEqualsToSetOf(derivedMulti, derivedMultiInterfaces)
    }

    @Test
    fun `Test caching results`() {
        val typeSystem = spyk(typeSystem)

        val topType = typeSystem.topType

        every { typeSystem.topTypeStream() } returns USupportTypeStream.from(typeSystem, topType)

        val typeStream = typeSystem.topTypeStream()
            .filterBySupertype(base1)

        typeStream.take100AndAssertEqualsToSetOf(base1, derived1A, derived1B)

        verify(exactly = 1) { typeSystem.findSubtypes(topType) }
        verify(exactly = 1) { typeSystem.findSubtypes(base1) }

        typeStream.take100AndAssertEqualsToSetOf(base1, derived1A, derived1B)

        verify(exactly = 1) { typeSystem.findSubtypes(topType) }
        verify(exactly = 1) { typeSystem.findSubtypes(base1) }
    }


    private fun <T> UTypeStream<T>.take100AndAssertEqualsToSetOf(vararg elements: T) {
        val set = elements.toSet()
        val result = take(100)
        assertEquals(result.size, set.size)
        assertEquals(result.toSet(), set)
    }
}
