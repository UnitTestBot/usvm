package org.usvm.types

import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.jacodb.api.JcType
import org.jacodb.api.ext.findClass
import org.jacodb.api.ext.toType
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.usvm.JcTypeSystem
import org.usvm.samples.JacoDBContainer
import org.usvm.samples.types.Hierarchy
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

    private val derivedInterface12 = cp.findClass<Hierarchy.DerivedInterface12>().toType()
    private val derived1A = cp.findClass<Hierarchy.Derived1A>().toType()
    private val derived1B = cp.findClass<Hierarchy.Derived1B>().toType()
    private val derivedMulti = cp.findClass<Hierarchy.DerivedMulti>().toType()

    private val comparable = cp.findClass<Comparable<*>>().toType()
    private val userComparable = cp.findClass<UserComparable>().toType()

    @Test
    fun `Test comparable`() {
        val top = typeSystem.topTypeStream()
            .filterBySupertype(cp.findClass<Comparable<*>>().toType())
        val result = mutableListOf<JcType>()
        val success = top.take(100, result)
        assertTrue(success)
        assertEquals(100, result.size)
        assertTrue(result.all(typeSystem::isInstantiable))
    }

    @Test
    fun `Test open class inheritance`() {
        val top = typeSystem.topTypeStream()
            .filterBySupertype(base1)
        val result = mutableListOf<JcType>()
        val success = top.take(100, result)
        assertTrue(success)
        assertEquals(setOf(base1, derived1A, derived1B), result.toSet())
    }

    @Test
    fun `Test interface inheritance`() {
        val top = typeSystem.topTypeStream()
            .filterBySupertype(interface1)
        val result = mutableListOf<JcType>()
        val success = top.take(100, result)
        assertTrue(success)
        assertEquals(setOf(derived1A, derivedMulti), result.toSet())
    }


    @Test
    fun `Test empty intersection`() {
        val top = typeSystem.topTypeStream()
            .filterBySupertype(base1)
            .filterBySupertype(base2)
        val result = mutableListOf<JcType>()
        val success = top.take(100, result)
        assertTrue(success)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `Test exact type`() {
        val top = typeSystem.topTypeStream()
            .filterBySupertype(derivedMulti)
            .filterBySubtype(derivedMulti)
        val result = mutableListOf<JcType>()
        val success = top.take(100, result)
        assertTrue(success)
        assertEquals(setOf(derivedMulti), result.toSet())
    }

    @Test
    fun `Test supertype & !supertype`() {
        val top = typeSystem.topTypeStream()
            .filterBySupertype(interface1)
            .filterByNotSupertype(interface2)
        val result = mutableListOf<JcType>()
        val success = top.take(100, result)
        assertTrue(success)
        assertEquals(setOf(derived1A), result.toSet())
    }

    @Test
    fun `Test comparable & user's interface inheritor`() { // works 100ms
        val top = typeSystem.topTypeStream()
            .filterBySupertype(comparable)
            .filterBySupertype(interface2)
        val result = mutableListOf<JcType>()
        val success = top.take(100, result)
        assertTrue(success)
        assertEquals(setOf(userComparable), result.toSet())
    }

    @Test
    fun `Test user's interface & comparable inheritor`() { // works less than 10ms
        val top = typeSystem.topTypeStream()
            .filterBySupertype(interface2)
            .filterBySupertype(comparable)
        val result = mutableListOf<JcType>()
        val success = top.take(100, result)
        assertTrue(success)
        assertEquals(setOf(userComparable), result.toSet())
    }

    @Test
    fun `Test caching results`() {
        val typeSystem = spyk(typeSystem)
        val top = typeSystem.topTypeStream()
            .filterBySupertype(base1)
        val result = mutableListOf<JcType>()

        val success1 = top.take(100, result)
        assertTrue(success1)
        assertEquals(setOf(base1, derived1A, derived1B), result.toSet())


        val success2 = top.take(100, result)
        assertTrue(success2)
        assertEquals(setOf(base1, derived1A, derived1B), result.toSet())

        verify(exactly = 1) { typeSystem.findSubTypes(base1) }
    }

    @Test
    fun `Test everything results`() {

    }
}
