package org.usvm

import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.ksmt.solver.model.DefaultValueSampler.Companion.sampleValue
import org.ksmt.utils.mkConst
import kotlin.test.assertSame

class TranslationTest {
    private lateinit var ctx: UContext
    private lateinit var heap: URegionHeap<Field, ArrayType>
    private lateinit var translator: UExprTranslator<Field, ArrayType>

    private lateinit var valueFieldDescr: Pair<Field, UBv32Sort>
    private lateinit var addressFieldDescr: Pair<Field, UAddressSort>
    private lateinit var valueArrayDescr: ArrayType
    private lateinit var addressArrayDescr: ArrayType

    @BeforeEach
    fun initializeContext() {
        ctx = UContext()
        heap = URegionHeap(ctx)
        translator = UExprTranslator(ctx)

        valueFieldDescr = mockk<Field>() to ctx.bv32Sort
        addressFieldDescr = mockk<Field>() to ctx.addressSort
        valueArrayDescr = mockk()
        addressArrayDescr = mockk()
    }

    @Test
    @Disabled("TODO: rebase on ref splitting PR")
    fun testTranslateConstAddressSort() = with(ctx) {
        val ref = mkConcreteHeapRef(heap.allocate())
        val idx = mkRegisterReading(0, sizeSort)

        val expr = heap.readArrayIndex(ref, idx, addressArrayDescr, addressSort)
        val translated = translator.translate(expr)

        assertSame(nullRef, translated)
    }

    @Test
    fun testTranslateConstValueSort() = with(ctx) {
        val ref = mkConcreteHeapRef(heap.allocate())
        val idx = mkRegisterReading(0, sizeSort)

        val expr = heap.readArrayIndex(ref, idx, valueArrayDescr, bv32Sort)
        val translated = translator.translate(expr)

        assertSame(bv32Sort.sampleValue(), translated)
    }

    @Test
    fun testTranslateWritingsToAllocatedArray() = with(ctx) {
        val ref = mkConcreteHeapRef(heap.allocate())
        val idx1 = mkRegisterReading(0, sizeSort)
        val idx2 = mkRegisterReading(1, sizeSort)

        val val1 = mkBv(1)
        val val2 = mkBv(2)

        heap.writeArrayIndex(ref, idx1, valueArrayDescr, bv32Sort, val1, trueExpr)
        heap.writeArrayIndex(ref, idx2, valueArrayDescr, bv32Sort, val2, trueExpr)

        val readIdx = mkRegisterReading(2, sizeSort)

        val expr = heap.readArrayIndex(ref, readIdx, valueArrayDescr, bv32Sort)

        val translated = translator.translate(expr)

        val translatedIdx1 = translator.translate(idx1)
        val translatedIdx2 = translator.translate(idx2)
        val translatedReadIdx = translator.translate(readIdx)

        val expected = mkArrayConst(mkArraySort(sizeSort, bv32Sort), bv32Sort.sampleValue())
            .store(translatedIdx1, val1)
            .store(translatedIdx2, val2)
            .select(translatedReadIdx)

        assertSame(expected, translated)
    }

    @Test
    fun testTranslate2dArray() = with(ctx) {
        var region = emptyInputArrayRegion(valueArrayDescr, bv32Sort) { (ref, idx), reg ->
            mkInputArrayReading(reg, ref, idx)
        }

        val ref1 = mkRegisterReading(0, addressSort)
        val idx1 = mkRegisterReading(1, sizeSort)
        val val1 = mkBv(1)

        val ref2 = mkRegisterReading(2, addressSort)
        val idx2 = mkRegisterReading(3, sizeSort)
        val val2 = mkBv(2)


        region = region.write(ref1 to idx1, val1, trueExpr)
        region = region.write(ref2 to idx2, val2, trueExpr)

        val ref3 = mkRegisterReading(4, addressSort)
        val idx3 = mkRegisterReading(5, sizeSort)

        val reading = region.read(ref3 to idx3)

        val translated = translator.translate(reading)

        val expected = mkArraySort(addressSort, sizeSort, bv32Sort)
            .mkConst(region.regionId.toString())
            .store(translator.translate(ref1), translator.translate(idx1), val1)
            .store(translator.translate(ref2), translator.translate(idx2), val2)
            .select(translator.translate(ref3), translator.translate(idx3))

        assertSame(expected, translated)
    }

//    @Test
    @RepeatedTest(30)
    fun testTranslateArrayCopy() = with(ctx) {
        var region = emptyInputArrayRegion(valueArrayDescr, bv32Sort) { (ref, idx), reg ->
            mkInputArrayReading(reg, ref, idx)
        }

        val ref1 = mkRegisterReading(0, addressSort)
        val idx1 = mkRegisterReading(1, sizeSort)
        val val1 = mkBv(1)

        val ref2 = mkRegisterReading(2, addressSort)
        val idx2 = mkRegisterReading(3, sizeSort)
        val val2 = mkBv(2)

        region = region.write(ref1 to idx1, val1, trueExpr)
        region = region.write(ref2 to idx2, val2, trueExpr)

        val concreteRef = mkConcreteHeapRef(heap.allocate())

        var concreteRegion = emptyAllocatedArrayRegion(valueArrayDescr, concreteRef.address, bv32Sort) { idx, reg ->
            mkAllocatedArrayReading(reg, idx)
        }
        val keyConverter = UInputToAllocatedKeyConverter(ref1 to mkBv(0), concreteRef to mkBv(0), mkBv(5))
        concreteRegion = concreteRegion.copyRange(region, mkBv(0), mkBv(5), keyConverter, trueExpr)

        val idx = mkRegisterReading(4, sizeSort)
        val reading = concreteRegion.read(idx)


        val key = region.regionId.keyMapper(translator)(keyConverter.convert(translator.translate(idx)))
        val innerReading =
            translator.translateRegionReading(region, key)
        val guard = translator.translate((mkBvSignedLessOrEqualExpr(mkBv(0), idx)) and mkBvSignedLessOrEqualExpr(idx, mkBv(5)))
        val expected = mkIte(guard, innerReading, bv32Sort.sampleValue())

        val translated = translator.translate(reading)

        assertSame(expected, translated)
    }
}