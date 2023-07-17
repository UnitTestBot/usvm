package org.usvm.memory

import io.ksmt.expr.rewrite.simplify.KExprSimplifier
import io.ksmt.utils.getValue
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.usvm.Field
import org.usvm.Type
import org.usvm.UAddressSort
import org.usvm.UBv32Sort
import org.usvm.UComponents
import org.usvm.UContext
import org.usvm.UInputFieldReading
import org.usvm.UIteExpr
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class HeapRefSplittingTest {
    private lateinit var ctx: UContext
    private lateinit var heap: URegionHeap<Field, Type>

    private lateinit var valueFieldDescr: Pair<Field, UBv32Sort>
    private lateinit var addressFieldDescr: Pair<Field, UAddressSort>
    private lateinit var arrayDescr: Pair<Type, UAddressSort>

    @BeforeEach
    fun initializeContext() {
        val components: UComponents<*, *, *> = mockk()
        every { components.mkTypeSystem(any()) } returns mockk()
        ctx = UContext(components)
        heap = URegionHeap(ctx)

        valueFieldDescr = mockk<Field>() to ctx.bv32Sort
        addressFieldDescr = mockk<Field>() to ctx.addressSort
        arrayDescr = mockk<Type>() to ctx.addressSort
    }

    @Test
    fun testConcreteWriting() = with(ctx) {
        val ref1 = heap.allocate()
        val ref2 = heap.allocate()

        val value1 = mkBv(0)
        val value2 = mkBv(1)

        heap.writeField(ref1, valueFieldDescr.first, valueFieldDescr.second, value1, trueExpr)
        heap.writeField(ref2, valueFieldDescr.first, valueFieldDescr.second, value2, trueExpr)

        val res1 = heap.readField(ref1, valueFieldDescr.first, valueFieldDescr.second)
        val res2 = heap.readField(ref2, valueFieldDescr.first, valueFieldDescr.second)

        assertSame(value1, res1)
        assertSame(value2, res2)
    }

    @Test
    fun testIteWriting() = with(ctx) {
        val ref1 = heap.allocate()
        val ref2 = mkRegisterReading(0, addressSort)

        val cond by boolSort

        val ref = mkIte(cond, ref1, ref2)

        val value = mkBv(1)

        heap.writeField(ref, valueFieldDescr.first, valueFieldDescr.second, value, trueExpr)

        val res = heap.readField(ref, valueFieldDescr.first, valueFieldDescr.second)

        assertIs<UIteExpr<UBv32Sort>>(res)
        assertEquals(cond, res.condition)
        assertSame(value, res.trueBranch)
        val reading = assertIs<UInputFieldReading<Field, UBv32Sort>>(res.falseBranch)
        assertEquals(!cond, reading.region.updates.single().guard)
    }

    @Test
    fun testInterleavedWritingToArray(): Unit = with(ctx) {
        val arrayRef = heap.allocate()

        val ref1 = heap.allocate()
        val ref2 = mkRegisterReading(0, addressSort)

        val idx1 by sizeSort
        val idx2 by sizeSort

        heap.writeArrayIndex(arrayRef, idx1, arrayDescr.first, arrayDescr.second, ref1, trueExpr)
        heap.writeArrayIndex(arrayRef, idx2, arrayDescr.first, arrayDescr.second, ref2, trueExpr)

        val readIdx by sizeSort
        val readValue = heap.readArrayIndex(arrayRef, readIdx, arrayDescr.first, arrayDescr.second)

        val value = mkBv(1)
        heap.writeField(readValue, valueFieldDescr.first, valueFieldDescr.second, value, trueExpr)

        val res1 = heap.readField(ref1, valueFieldDescr.first, valueFieldDescr.second)
        assertIs<UIteExpr<UBv32Sort>>(res1)

        val res2 = heap.readField(ref2, valueFieldDescr.first, valueFieldDescr.second)
        assertIs<UInputFieldReading<Field, UBv32Sort>>(res2)
    }

    @Test
    fun testSeveralWritingsToArray() = with(ctx) {
        val ref = heap.allocate()

        val idx1 = mkRegisterReading(0, sizeSort)
        val idx2 = mkRegisterReading(1, sizeSort)
        val idx3 = mkRegisterReading(2, sizeSort)

        val val1 = heap.allocate()
        val val2 = heap.allocate()
        val val3 = heap.allocate()

        heap.writeArrayIndex(ref, idx1, arrayDescr.first, arrayDescr.second, val1, trueExpr)
        heap.writeArrayIndex(ref, idx2, arrayDescr.first, arrayDescr.second, val2, trueExpr)
        heap.writeArrayIndex(ref, idx3, arrayDescr.first, arrayDescr.second, val3, trueExpr)

        val res1 = heap.readArrayIndex(ref, idx1, arrayDescr.first, arrayDescr.second)

        val (concreteRefs, symbolicRef) = splitUHeapRef(res1, ignoreNullRefs = false)
        assertNotNull(symbolicRef)

        assertEquals(3, concreteRefs.size)
        assertSame(nullRef, symbolicRef.expr)
    }

    @Test
    fun testWritingIteToArrayByIteIndex() = with(ctx) {
        val ref1 = heap.allocate()
        val ref2 = heap.allocate()
        val cond1 by boolSort
        val ref = mkIte(cond1, ref1, ref2)

        val idx = mkRegisterReading(0, sizeSort)


        val val1 = heap.allocate()
        val val2 = heap.allocate()
        val cond2 by boolSort
        val value = mkIte(cond2, val1, val2)

        heap.writeArrayIndex(ref, idx, arrayDescr.first, arrayDescr.second, value, trueExpr)


        val res1 = heap.readArrayIndex(ref1, idx, arrayDescr.first, arrayDescr.second)

        val (concreteRefs, _) = splitUHeapRef(res1, ignoreNullRefs = false)

        assertEquals(2, concreteRefs.size)
        assertSame(val2, concreteRefs[0].expr)
        assertSame(cond1 and !cond2, concreteRefs[0].guard)
        // we need expr simplifier here, because mkAndNoFlatten produces too complicated expression
        assertSame(val1, concreteRefs[1].expr)
        assertSame(!(cond1 and !cond2) and cond1 and cond2, KExprSimplifier(this).apply(concreteRefs[1].guard))
    }

    @Test
    fun testInterleavedWritingToField() = with(ctx) {
        val ref1 = mkRegisterReading(0, addressSort)
        val ref2 = mkRegisterReading(1, addressSort)
        val ref3 = mkRegisterReading(2, addressSort)

        val val1 = heap.allocate()
        val val2 = mkRegisterReading(3, addressSort)
        val val3 = heap.allocate()

        heap.writeField(ref1, addressFieldDescr.first, addressFieldDescr.second, val1, trueExpr)
        heap.writeField(ref2, addressFieldDescr.first, addressFieldDescr.second, val2, trueExpr)
        heap.writeField(ref3, addressFieldDescr.first, addressFieldDescr.second, val3, trueExpr)

        val res1 = heap.readField(ref1, addressFieldDescr.first, addressFieldDescr.second)
        assertIs<UIteExpr<UAddressSort>>(res1)

        val res2 = heap.readField(ref2, addressFieldDescr.first, addressFieldDescr.second)
        assertIs<UIteExpr<UAddressSort>>(res2)

        val res3 = heap.readField(ref3, addressFieldDescr.first, addressFieldDescr.second)
        assertSame(val3, res3)
    }

    @Test
    fun testInterleavedWritingToArrayButNoMatchedUpdates() = with(ctx) {
        val arrayRef = heap.allocate()

        val ref1 = mkRegisterReading(0, addressSort)
        val ref2 = mkRegisterReading(1, addressSort)

        val idx1 by sizeSort
        val idx2 by sizeSort

        heap.writeArrayIndex(arrayRef, idx1, arrayDescr.first, arrayDescr.second, ref1, trueExpr)
        heap.writeArrayIndex(arrayRef, idx2, arrayDescr.first, arrayDescr.second, ref2, trueExpr)

        val readIdx by sizeSort
        val readValue = heap.readArrayIndex(arrayRef, readIdx, arrayDescr.first, arrayDescr.second)

        val value = mkBv(1)
        heap.writeField(readValue, valueFieldDescr.first, valueFieldDescr.second, value, trueExpr)

        val res1 = heap.readField(ref1, valueFieldDescr.first, valueFieldDescr.second)
        assertIs<UInputFieldReading<Field, UBv32Sort>>(res1)

        val res2 = heap.readField(ref2, valueFieldDescr.first, valueFieldDescr.second)
        assertIs<UInputFieldReading<Field, UBv32Sort>>(res2)

        assertEquals(res1.region, res2.region)
    }

    @Test
    fun testInterleavedWritingToFieldButNoMatchedUpdates() = with(ctx) {
        val ref1 = mkRegisterReading(0, addressSort)
        val ref2 = mkRegisterReading(1, addressSort)
        val ref3 = mkRegisterReading(2, addressSort)

        val val1 = mkRegisterReading(3, addressSort)
        val val2 = mkRegisterReading(4, addressSort)
        val val3 = mkRegisterReading(5, addressSort)

        heap.writeField(ref1, addressFieldDescr.first, addressFieldDescr.second, val1, trueExpr)
        heap.writeField(ref2, addressFieldDescr.first, addressFieldDescr.second, val2, trueExpr)
        heap.writeField(ref3, addressFieldDescr.first, addressFieldDescr.second, val3, trueExpr)

        val res1 = heap.readField(ref1, addressFieldDescr.first, addressFieldDescr.second)
        assertIs<UInputFieldReading<Field, UBv32Sort>>(res1)

        val res2 = heap.readField(ref2, addressFieldDescr.first, addressFieldDescr.second)
        assertIs<UInputFieldReading<Field, UBv32Sort>>(res2)

        assertEquals(res1.region, res2.region)

        val res3 = heap.readField(ref3, addressFieldDescr.first, addressFieldDescr.second)
        assertSame(val3, res3)
    }

    @Test
    fun testInterleavedValueWriting() = with(ctx) {
        val ref1 = mkRegisterReading(0, addressSort)
        val ref2 = heap.allocate()

        val cond by boolSort

        val val1 = mkIte(cond, ref1, ref2)

        heap.writeField(ref1, addressFieldDescr.first, addressFieldDescr.second, val1, trueExpr)

        val ref3 = mkRegisterReading(1, addressSort)

        val res1 = heap.readField(ref3, addressFieldDescr.first, addressFieldDescr.second)
        assertIs<UIteExpr<UAddressSort>>(res1)

        val valueToWrite = mkBv(3)
        heap.writeField(res1, valueFieldDescr.first, valueFieldDescr.second, valueToWrite, trueExpr)

        val readedValue = heap.readField(ref2, valueFieldDescr.first, valueFieldDescr.second)
        val ite = assertIs<UIteExpr<UBv32Sort>>(readedValue)

        assertSame(ite.condition, (ref1 eq ref3) and !cond)
        assertSame(ite.trueBranch, valueToWrite)
    }
}