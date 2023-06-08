package org.usvm.types

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.jacodb.api.JcType
import org.jacodb.api.ext.findClass
import org.jacodb.api.ext.toType
import org.junit.jupiter.api.Test
import org.usvm.JcTypeStream
import org.usvm.JcTypeSystem
import org.usvm.samples.JacoDBContainer
import org.usvm.samples.types.Hierarchy
import org.usvm.samples.types.Hierarchy.DerivedMultiInterfaces
import org.usvm.samples.types.Hierarchy.UserComparable
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JcTypeStreamTest {
    private val key = "typesTest"

    private val cp = JacoDBContainer(key).cp
    private val typeSystem = JcTypeSystem(cp)

    private val base1 = cp.findClass<Hierarchy.Base1>().toType()
    private val base2 = cp.findClass<Hierarchy.Base2>().toType()

    private val interface1 = cp.findClass<Hierarchy.Interface1>().toType()
    private val interface2 = cp.findClass<Hierarchy.Interface2>().toType()

    private val derived1A = cp.findClass<Hierarchy.Derived1A>().toType()
    private val derived1B = cp.findClass<Hierarchy.Derived1B>().toType()
    private val derivedMulti = cp.findClass<Hierarchy.DerivedMulti>().toType()

    private val comparable = cp.findClass<Comparable<*>>().toType()
    private val userComparable = cp.findClass<UserComparable>().toType()

    private val derivedMultiInterfaces = cp.findClass<DerivedMultiInterfaces>().toType()

    @Test
    fun `Test comparable`() {
        val typeStream = typeSystem.topTypeStream()
            .filterBySupertype(cp.findClass<Comparable<*>>().toType())
        val result = mutableListOf<JcType>()
        val success = typeStream.take(100, result)
        assertTrue(success)
        assertEquals(100, result.size)
        assertTrue(result.all(typeSystem::isInstantiable))
    }

    @Test
    fun `Test open class inheritance`() {
        val typeStream = typeSystem.topTypeStream()
            .filterBySupertype(base1)
        val result = mutableListOf<JcType>()
        val success = typeStream.take(100, result)
        assertTrue(success)
        assertEquals(setOf(base1, derived1A, derived1B), result.toSet())
    }

    @Test
    fun `Test interface inheritance`() {
        val typeStream = typeSystem.topTypeStream()
            .filterBySupertype(interface1)
        val result = mutableListOf<JcType>()
        val success = typeStream.take(100, result)
        assertTrue(success)
        assertEquals(setOf(derived1A, derivedMulti, derivedMultiInterfaces), result.toSet())
    }


    @Test
    fun `Test empty intersection`() {
        val typeStream = typeSystem.topTypeStream()
            .filterBySupertype(base1)
            .filterBySupertype(base2)
        val result = mutableListOf<JcType>()
        val success = typeStream.take(100, result)
        assertTrue(success)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `Test exact type`() {
        val typeStream = typeSystem.topTypeStream()
            .filterBySupertype(derivedMulti)
            .filterBySubtype(derivedMulti)
        val result = mutableListOf<JcType>()
        val success = typeStream.take(100, result)
        assertTrue(success)
        assertEquals(setOf(derivedMulti), result.toSet())
    }

    @Test
    fun `Test supertype & !supertype`() {
        val typeStream = typeSystem.topTypeStream()
            .filterBySupertype(interface1)
            .filterByNotSupertype(interface2)
        val result = mutableListOf<JcType>()
        val success = typeStream.take(100, result)
        assertTrue(success)
        assertEquals(setOf(derived1A), result.toSet())
    }

    @Test
    fun `Test comparable & user's interface inheritor`() { // works 100ms
        val typeStream = typeSystem.topTypeStream()
            .filterBySupertype(comparable)
            .filterBySupertype(interface2)
        val result = mutableListOf<JcType>()
        val success = typeStream.take(100, result)
        assertTrue(success)
        assertEquals(setOf(userComparable), result.toSet())
    }

    @Test
    fun `Test user's interface & comparable inheritor`() { // works less than 10ms
        val typeStream = typeSystem.topTypeStream()
            .filterBySupertype(interface2)
            .filterBySupertype(comparable)
        val result = mutableListOf<JcType>()
        val success = typeStream.take(100, result)
        assertTrue(success)
        assertEquals(setOf(userComparable), result.toSet())
    }

    @Test
    fun `Test caching results`() {
        val typeSystem = mockk<JcTypeSystem>()

        val jcObject = cp.findClass<Any>().toType()
        every { typeSystem.topTypeStream() } answers {
            JcTypeStream.from(typeSystem, jcObject)
        }
        every {
            typeSystem.isInstantiable(any())
        } answers {
            this@JcTypeStreamTest.typeSystem.isInstantiable(firstArg())
        }

        every {
            typeSystem.isSupertype(any(), any())
        } answers {
            this@JcTypeStreamTest.typeSystem.isSupertype(firstArg(), secondArg())
        }


        every {
            typeSystem.findSubTypes(any())
        } answers {
            this@JcTypeStreamTest.typeSystem.findSubTypes(firstArg())
        }

        val typeStream = typeSystem.topTypeStream()
            .filterBySupertype(base1)
        val result = mutableListOf<JcType>()

        val success1 = typeStream.take(100, result)
        assertTrue(success1)
        assertEquals(setOf(base1, derived1A, derived1B), result.toSet())


        val success2 = typeStream.take(100, result)
        assertTrue(success2)
        assertEquals(setOf(base1, derived1A, derived1B), result.toSet())
        verify(exactly = 1) { typeSystem.findSubTypes(jcObject) }
        verify(exactly = 1) { typeSystem.findSubTypes(base1) }
    }

    @Test
    fun `Test subtype1 & subtype2 & subtype3`() {
        val typeStream = typeSystem.topTypeStream()
            .filterBySupertype(base2)
            .filterBySupertype(interface1)
            .filterBySupertype(interface2)

        val result = mutableListOf<JcType>()
        val success = typeStream.take(100, result)
        assertTrue(success)

        assertEquals(setOf(derivedMulti, derivedMultiInterfaces), result.toSet())
    }
}
