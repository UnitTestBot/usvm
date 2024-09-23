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
import org.usvm.UBv32SizeExprProvider
import org.usvm.UBv32Sort
import org.usvm.UComponents
import org.usvm.UContext
import org.usvm.UIteExpr
import org.usvm.USizeSort
import org.usvm.UConcreteHeapRef
import org.usvm.api.allocateConcreteRef
import org.usvm.api.readArrayIndex
import org.usvm.api.readField
import org.usvm.api.writeArrayIndex
import org.usvm.api.writeField
import org.usvm.collection.field.UInputFieldReading
import org.usvm.sizeSort
import org.usvm.mkSizeExpr
import org.usvm.api.memcpy
import org.usvm.api.allocateArray
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.constraints.UEqualityConstraints
import org.usvm.constraints.UTypeConstraints
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class HeapRefSplittingTest {
    private lateinit var ctx: UContext<USizeSort>
    private lateinit var heap: UMemory<Type, Any>
    private lateinit var ownership: MutabilityOwnership

    private lateinit var valueFieldDescr: Pair<Field, UBv32Sort>
    private lateinit var addressFieldDescr: Pair<Field, UAddressSort>
    private lateinit var arrayDescr: Pair<Type, UAddressSort>

    @BeforeEach
    fun initializeContext() {
        val components: UComponents<Type, USizeSort> = mockk()
        every { components.mkTypeSystem(any()) } returns mockk()
        ctx = UContext(components)
        ownership = MutabilityOwnership()
        every { components.mkSizeExprProvider(any()) } answers { UBv32SizeExprProvider(ctx) }
        val eqConstraints = UEqualityConstraints(ctx, ownership)
        val typeConstraints = UTypeConstraints(ownership, components.mkTypeSystem(ctx), eqConstraints)
        heap = UMemory(ctx, ownership, typeConstraints)

        valueFieldDescr = mockk<Field>() to ctx.bv32Sort
        addressFieldDescr = mockk<Field>() to ctx.addressSort
        arrayDescr = mockk<Type>() to ctx.addressSort
    }

    @Test
    fun testConcreteWriting() = with(ctx) {
        val ref1 = allocateConcreteRef()
        val ref2 = allocateConcreteRef()

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
        val ref1 = allocateConcreteRef()
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
        assertEquals(!cond, reading.collection.updates.single().guard)
    }

    @Test
    fun testInterleavedWritingToArray(): Unit = with(ctx) {
        val arrayRef = allocateConcreteRef()

        val ref1 = allocateConcreteRef()
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
        val ref = allocateConcreteRef()

        val idx1 = mkRegisterReading(0, sizeSort)
        val idx2 = mkRegisterReading(1, sizeSort)
        val idx3 = mkRegisterReading(2, sizeSort)

        val val1 = allocateConcreteRef()
        val val2 = allocateConcreteRef()
        val val3 = allocateConcreteRef()

        heap.writeArrayIndex(ref, idx1, arrayDescr.first, arrayDescr.second, val1, trueExpr)
        heap.writeArrayIndex(ref, idx2, arrayDescr.first, arrayDescr.second, val2, trueExpr)
        heap.writeArrayIndex(ref, idx3, arrayDescr.first, arrayDescr.second, val3, trueExpr)

        val res1 = heap.readArrayIndex(ref, idx1, arrayDescr.first, arrayDescr.second)

        val (concreteRefs, symbolicRefs) = splitUHeapRef(res1, ignoreNullRefs = false)
        val symbolicRef = symbolicRefs.single()
        assertNotNull(symbolicRef)

        assertEquals(3, concreteRefs.size)
        assertSame(nullRef, symbolicRef.expr)
    }

    @Test
    fun testIrrelevantRecordsDoNotAppearInReading() = with(ctx) {
        val ref = allocateConcreteRef()
        val idx0 = mkRegisterReading(0, sizeSort)
        val idx1 = mkRegisterReading(1, sizeSort)
        val idx2 = mkRegisterReading(2, sizeSort)
        val idx3 = mkRegisterReading(3, sizeSort)
        val symbolicValue = mkRegisterReading(1, addressSort)
        val concreteValue1 = allocateConcreteRef()
        val concreteValue2 = allocateConcreteRef()
        val concreteValue3 = allocateConcreteRef()

        heap.writeArrayIndex(ref, idx0, arrayDescr.first, arrayDescr.second, symbolicValue, trueExpr)
        heap.writeArrayIndex(ref, idx1, arrayDescr.first, arrayDescr.second, concreteValue1, trueExpr)
        heap.writeArrayIndex(ref, idx2, arrayDescr.first, arrayDescr.second, concreteValue2, trueExpr)
        heap.writeArrayIndex(ref, idx3, arrayDescr.first, arrayDescr.second, concreteValue3, trueExpr)
        val reading = heap.readArrayIndex(ref, idx2, arrayDescr.first, arrayDescr.second)
        val (concretes, symbolic) = splitUHeapRef(reading, ignoreNullRefs = false)
        assertEquals(2, concretes.size)
        assertEquals(0, symbolic.size)
        assertSame(concreteValue3, concretes[0].expr)
        assertSame(concreteValue2, concretes[1].expr)
    }

    @Test
    fun testLastWriteUnderInvariantTree() = with(ctx) {
        val ref = allocateConcreteRef()
        val idx1 = mkRegisterReading(0, sizeSort)
        val idx2 = mkRegisterReading(1, sizeSort)

        val concreteValue = allocateConcreteRef()
        val symbolicValue = mkRegisterReading(3, addressSort)

        heap.writeArrayIndex(ref, idx1, arrayDescr.first, arrayDescr.second, concreteValue, trueExpr)
        heap.writeArrayIndex(ref, idx2, arrayDescr.first, arrayDescr.second, symbolicValue, trueExpr)

        val read2 = heap.readArrayIndex(ref, idx2, arrayDescr.first, arrayDescr.second)
        assertSame(symbolicValue, read2)

        val read1 = heap.readArrayIndex(ref, idx1, arrayDescr.first, arrayDescr.second)
        val (concreteRefs, symbolicRefs) = splitUHeapRef(read1, ignoreNullRefs = false)
        assertEquals(1, concreteRefs.size)
        assertEquals(1, symbolicRefs.size)
        assertSame(concreteValue, concreteRefs[0].expr)
    }

    @Test
    fun testLastWriteUnderInvariantTreeWithRegionsSplit() = with(ctx) {
        val ref = allocateConcreteRef()
        val idx1 = mkRegisterReading(0, sizeSort)
        val idx2 = mkRegisterReading(1, sizeSort)
        val idx3 = ctx.mkSizeExpr(1)

        val concreteValue1 = allocateConcreteRef()
        val concreteValue2 = allocateConcreteRef()
        val symbolicValue = mkRegisterReading(4, addressSort)

        heap.writeArrayIndex(ref, idx1, arrayDescr.first, arrayDescr.second, concreteValue1, trueExpr)
        heap.writeArrayIndex(ref, idx2, arrayDescr.first, arrayDescr.second, symbolicValue, trueExpr)
        heap.writeArrayIndex(ref, idx3, arrayDescr.first, arrayDescr.second, concreteValue2, trueExpr)

        val read3 = heap.readArrayIndex(ref, idx3, arrayDescr.first, arrayDescr.second)
        assertSame(concreteValue2, read3)

        val read2 = heap.readArrayIndex(ref, idx2, arrayDescr.first, arrayDescr.second)
        val (concreteRefs, symbolicRefs) = splitUHeapRef(read2, ignoreNullRefs = false)
        assertEquals(1, concreteRefs.size)
        assertEquals(1, symbolicRefs.size)
        assertSame(concreteValue2, concreteRefs[0].expr)
        assertSame(symbolicValue, symbolicRefs[0].expr)
    }

    @Test
    fun testLastWriteUnderRangedWrite() = with(ctx) {
        val dstRef = allocateConcreteRef()
        val idx0 = mkRegisterReading(0, sizeSort)
        val symbolicRef = mkRegisterReading(12, addressSort)

        val (array, srcRef) = initializeArray()
        val srcFrom = 0
        val dstTo = array.size - 1
        val dstFrom = 0
        heap.memcpy(
            srcRef = srcRef,
            dstRef = dstRef,
            type = arrayDescr.first,
            elementSort = arrayDescr.second,
            fromSrcIdx = ctx.mkSizeExpr(srcFrom),
            fromDstIdx = ctx.mkSizeExpr(dstFrom),
            toDstIdx = ctx.mkSizeExpr(dstTo - 1),
            guard = ctx.trueExpr
        )

        val read1 = heap.readArrayIndex(dstRef, idx0, arrayDescr.first, arrayDescr.second)
        val (concreteBefore, _) = splitUHeapRef(read1, ignoreNullRefs = false)
        assertEquals(array.size, concreteBefore.size)

        heap.writeArrayIndex(dstRef, idx0, arrayDescr.first, arrayDescr.second, symbolicRef, trueExpr)
        val read2 = heap.readArrayIndex(dstRef, idx0, arrayDescr.first, arrayDescr.second)
        assertSame(symbolicRef, read2)

        val idx1 = mkRegisterReading(1, sizeSort)
        val read3 = heap.readArrayIndex(dstRef, idx1, arrayDescr.first, arrayDescr.second)
        val (concreteAfter, _) = splitUHeapRef(read3, ignoreNullRefs = false)
        assertEquals(array.size, concreteAfter.size)
    }

    @Test
    fun testLastWriteAboveRangedWrite() = with(ctx) {
        val dstRef = allocateConcreteRef()
        val idx0 = mkRegisterReading(0, sizeSort)
        val concreteValue = allocateConcreteRef()

        val (array, srcRef) = initializeArray()
        val srcFrom = 0
        val dstTo = array.size - 1
        val dstFrom = 0
        heap.memcpy(
            srcRef = srcRef,
            dstRef = dstRef,
            type = arrayDescr.first,
            elementSort = arrayDescr.second,
            fromSrcIdx = ctx.mkSizeExpr(srcFrom),
            fromDstIdx = ctx.mkSizeExpr(dstFrom),
            toDstIdx = ctx.mkSizeExpr(dstTo - 1),
            guard = ctx.trueExpr
        )

        heap.writeArrayIndex(dstRef, idx0, arrayDescr.first, arrayDescr.second, concreteValue, trueExpr)
        val read1 = heap.readArrayIndex(dstRef, idx0, arrayDescr.first, arrayDescr.second)
        assertSame(concreteValue, read1)

        val idx1 = mkRegisterReading(1, sizeSort)
        val read2 = heap.readArrayIndex(dstRef, idx1, arrayDescr.first, arrayDescr.second)
        val (concretes2, _) = splitUHeapRef(read2, ignoreNullRefs = false)
        assertEquals(array.size + 1, concretes2.size)
    }

    @Test
    fun testWritingIteToArrayByIteIndex() = with(ctx) {
        val ref1 = allocateConcreteRef()
        val ref2 = allocateConcreteRef()
        val cond1 by boolSort
        val ref = mkIte(cond1, ref1, ref2)

        val idx = mkRegisterReading(0, sizeSort)


        val val1 = allocateConcreteRef()
        val val2 = allocateConcreteRef()
        val cond2 by boolSort
        val value = mkIte(cond2, val1, val2)

        heap.writeArrayIndex(ref, idx, arrayDescr.first, arrayDescr.second, value, trueExpr)


        val res1 = heap.readArrayIndex(ref1, idx, arrayDescr.first, arrayDescr.second)

        val (concreteRefs, _) = splitUHeapRef(res1, ignoreNullRefs = false)

        assertEquals(2, concreteRefs.size)
        assertSame(val2, concreteRefs[0].expr)
        assertSame(cond1 and !cond2, concreteRefs[0].guard)
        // we need expr simplifier here, because mkAnd with flat=false produces too complicated expression
        assertSame(val1, concreteRefs[1].expr)
        assertSame(!(cond1 and !cond2) and cond1 and cond2, KExprSimplifier(this).apply(concreteRefs[1].guard))
    }

    @Test
    fun testInterleavedWritingToField() = with(ctx) {
        val ref1 = mkRegisterReading(0, addressSort)
        val ref2 = mkRegisterReading(1, addressSort)
        val ref3 = mkRegisterReading(2, addressSort)

        val val1 = allocateConcreteRef()
        val val2 = mkRegisterReading(3, addressSort)
        val val3 = allocateConcreteRef()

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
        val arrayRef = allocateConcreteRef()

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

        assertEquals(res1.collection, res2.collection)
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

        val res1Updates = res1.collection.updates.toList()
        val res2Updates = res2.collection.updates.toList()

        assertEquals(3, res1Updates.size)
        assertEquals(2, res2Updates.size)
        assertEquals(res2Updates, res1Updates.subList(1, 3))

        val res3 = heap.readField(ref3, addressFieldDescr.first, addressFieldDescr.second)
        assertSame(val3, res3)
    }

    @Test
    fun testInterleavedValueWriting() = with(ctx) {
        val ref1 = mkRegisterReading(0, addressSort)
        val ref2 = allocateConcreteRef()

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

    private fun initializeArray(): Pair<IntArray, UConcreteHeapRef> {
        val array = IntArray(10)
        val ref = heap.allocateArray(arrayDescr.first, ctx.sizeSort, ctx.mkSizeExpr(array.size))

        array.indices.forEach { idx ->
            heap.writeArrayIndex(
                ref = ref,
                index = ctx.mkSizeExpr(idx),
                type = arrayDescr.first,
                sort = arrayDescr.second,
                value = ctx.allocateConcreteRef(),
                guard = ctx.trueExpr
            )
        }

        return array to ref
    }
}
