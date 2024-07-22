package org.usvm.memory

import io.ksmt.utils.getValue
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.usvm.Type
import org.usvm.UComponents
import org.usvm.UContext
import org.usvm.USizeSort
import org.usvm.api.allocateConcreteRef
import org.usvm.collections.immutable.internal.MutabilityOwnership
import kotlin.test.assertSame

class HeapRefEqTest {
    private lateinit var ctx: UContext<USizeSort>
    private lateinit var heap: UMemory<Type, Any>
    private lateinit var ownership: MutabilityOwnership

    @BeforeEach
    fun initializeContext() {
        val components: UComponents<Type, USizeSort> = mockk()
        every { components.mkTypeSystem(any()) } returns mockk()
        ctx = UContext(components)
        ownership = MutabilityOwnership()
        heap = UMemory(ctx, ownership, mockk())
    }

    @Test
    fun testSymbolicHeapRefEqFalse() = with(ctx) {
        val ref1 = mkRegisterReading(0, addressSort)
        val ref2 = mkRegisterReading(1, addressSort)
        val expr = mkHeapRefEq(ref1, ref2)
        assertSame(ref1 eq ref2, expr)
    }

    @Test
    fun testSymbolicWithNullRefHeapRefEqFalse() = with(ctx) {
        val ref1 = mkRegisterReading(0, addressSort)
        val ref2 = nullRef
        val expr = mkHeapRefEq(ref1, ref2)
        assertSame(ref1 eq ref2, expr)
    }

    @Test
    fun testSymbolicAndConcreteHeapRefEq() = with(ctx) {
        val ref1 = allocateConcreteRef()
        val ref2 = mkRegisterReading(0, addressSort)
        val expr = mkHeapRefEq(ref1, ref2)
        assertSame(falseExpr, expr)
    }

    @Test
    fun testSymbolicHeapRefEqTrue() = with(ctx) {
        val ref1 = mkRegisterReading(0, addressSort)
        val ref2 = mkRegisterReading(0, addressSort)
        val expr = mkHeapRefEq(ref1, ref2)
        assertSame(trueExpr, expr)
    }

    @Test
    fun testConcreteHeapRefEqFalse() = with(ctx) {
        val ref1 = allocateConcreteRef()
        val ref2 = allocateConcreteRef()
        val expr = mkHeapRefEq(ref1, ref2)
        assertSame(falseExpr, expr)
    }

    @Test
    fun testConcreteHeapRefEqTrue() = with(ctx) {
        val ref1 = allocateConcreteRef()
        val ref2 = ref1
        val expr = mkHeapRefEq(ref1, ref2)
        assertSame(trueExpr, expr)
    }

    @Test
    fun testInterleavedWithNullHeapRefEq() = with(ctx) {
        val ref1 = allocateConcreteRef()
        val ref2 = nullRef
        val expr = mkHeapRefEq(ref1, ref2)
        assertSame(falseExpr, expr)
    }

    @Test
    fun testInterleavedHeapRefEq() = with(ctx) {
        val ref1 = allocateConcreteRef()
        val ref2 = mkRegisterReading(0, addressSort)
        val expr = mkHeapRefEq(ref1, ref2)
        assertSame(falseExpr, expr)
    }

    @Test
    fun testSimpleIteConcreteHeapRefEq() = with(ctx) {
        val ref1 = allocateConcreteRef()
        val ref2 = ref1
        val cond1 by boolSort

        val ref3 = allocateConcreteRef()
        val ref4 = ref3
        val cond2  by boolSort

        val expr = mkHeapRefEq(mkIte(cond1, ref1, ref2), mkIte(cond2, ref3, ref4))

        assertSame(falseExpr, expr)
    }

    @Test
    fun testSimpleIteSymbolicHeapRefEq() = with(ctx) {
        val ref1 = mkRegisterReading(0, addressSort)
        val ref2 = mkRegisterReading(1, addressSort)
        val cond1 by boolSort

        val ref3 = mkRegisterReading(2, addressSort)

        val expr = mkHeapRefEq(mkIte(cond1, ref1, ref2), ref3)

        assertSame(mkIte(cond1, ref1, ref2) eq ref3, expr)
    }


    @Test
    fun testSimpleIteHeapRefEq() = with(ctx) {
        val ref1 = allocateConcreteRef()
        val ref2 = mkRegisterReading(0, addressSort)
        val cond1 by boolSort

        val ref3 = ref1
        val ref4 = ref2
        val cond2  by boolSort

        val expr = mkHeapRefEq(mkIte(cond1, ref1, ref2), mkIte(cond2, ref3, ref4))

        assertSame(mkOr(cond1 and cond2, !cond1 and !cond2), expr)
    }

    @Test
    fun testMultiIteHeapRefEq() = with(ctx) {
        val symbolicRef1 = mkRegisterReading(0, addressSort)
        val symbolicRef2 = mkRegisterReading(1, addressSort)
        val symbolicRef3 = mkRegisterReading(2, addressSort)

        val concreteRef1 = allocateConcreteRef()
        val concreteRef2 = allocateConcreteRef()

        val cond1 by boolSort
        val cond2 by boolSort
        val cond3 by boolSort
        val cond4 by boolSort

        val ite1 = mkIte(cond1, symbolicRef1, mkIte(cond2, symbolicRef2, concreteRef1))
        val ite2 = mkIte(cond3, symbolicRef3, mkIte(cond4, concreteRef1, concreteRef2))
        val refsEq = mkHeapRefEq(ite1, ite2)

        val concreteRef1EqConcreteRef2 = mkAnd(!cond1, !cond2, !cond3, cond4)
        val symbolicRefEqGuard = mkAnd(cond1 or cond2, cond3)
        val symbolicIteEq = mkIte(cond1, symbolicRef1, symbolicRef2) eq symbolicRef3
        val expected = concreteRef1EqConcreteRef2 or (symbolicRefEqGuard and symbolicIteEq)
        assertSame(expected, refsEq)
    }
}
