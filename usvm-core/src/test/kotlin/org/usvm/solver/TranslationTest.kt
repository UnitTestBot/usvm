package org.usvm.solver

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import io.ksmt.solver.KSolverStatus
import io.ksmt.solver.z3.KZ3Solver
import io.ksmt.utils.mkConst
import org.usvm.Field
import org.usvm.Type
import org.usvm.UAddressSort
import org.usvm.UBv32Sort
import org.usvm.UComponents
import org.usvm.UContext
import org.usvm.memory.UInputToAllocatedKeyConverter
import org.usvm.memory.UInputToInputKeyConverter
import org.usvm.memory.URegionHeap
import org.usvm.memory.emptyAllocatedArrayRegion
import org.usvm.memory.emptyInputArrayLengthRegion
import org.usvm.memory.emptyInputArrayRegion
import org.usvm.memory.emptyInputFieldRegion
import kotlin.test.assertSame

class TranslationTest {
    private lateinit var ctx: UContext
    private lateinit var heap: URegionHeap<Field, Type>
    private lateinit var translator: UExprTranslator<Field, Type>

    private lateinit var valueFieldDescr: Pair<Field, UBv32Sort>
    private lateinit var addressFieldDescr: Pair<Field, UAddressSort>
    private lateinit var valueArrayDescr: Type
    private lateinit var addressArrayDescr: Type

    @BeforeEach
    fun initializeContext() {
        val components: UComponents<*, *, *> = mockk()
        every { components.mkTypeSystem(any()) } returns mockk()

        ctx = UContext(components)
        heap = URegionHeap(ctx)
        translator = UExprTranslator(ctx)

        valueFieldDescr = mockk<Field>() to ctx.bv32Sort
        addressFieldDescr = mockk<Field>() to ctx.addressSort
        valueArrayDescr = mockk()
        addressArrayDescr = mockk()
    }

    @Test
    fun testTranslateConstAddressSort() = with(ctx) {
        val ref = heap.allocate()
        val idx = mkRegisterReading(0, sizeSort)

        val expr = heap.readArrayIndex(ref, idx, addressArrayDescr, addressSort)
        val translated = translator.translate(expr)

        assertSame(translator.translate(nullRef), translated)
    }

    @Test
    fun testTranslateConstValueSort() = with(ctx) {
        val ref = heap.allocate()
        val idx = mkRegisterReading(0, sizeSort)

        val expr = heap.readArrayIndex(ref, idx, valueArrayDescr, bv32Sort)
        val translated = translator.translate(expr)

        assertSame(mkBv(0), translated)
    }

    @Test
    fun testTranslateWritingsToAllocatedArray() = with(ctx) {
        val ref = heap.allocate()
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

        val expected = mkArrayConst(mkArraySort(sizeSort, bv32Sort), mkBv(0))
            .store(translatedIdx1, val1)
            .store(translatedIdx2, val2)
            .select(translatedReadIdx)

        assertSame(expected, translated)
    }

    @Test
    fun testTranslate2DArray() = with(ctx) {
        val ref1 = mkRegisterReading(0, addressSort)
        val idx1 = mkRegisterReading(1, sizeSort)
        val val1 = mkBv(1)

        val ref2 = mkRegisterReading(2, addressSort)
        val idx2 = mkRegisterReading(3, sizeSort)
        val val2 = mkBv(2)


        val region = emptyInputArrayRegion(valueArrayDescr, bv32Sort)
            .write(ref1 to idx1, val1, trueExpr)
            .write(ref2 to idx2, val2, trueExpr)

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

    @RepeatedTest(10)
    fun testTranslateInputToAllocatedArrayCopy() = with(ctx) {

        val ref1 = mkRegisterReading(0, addressSort)
        val idx1 = mkRegisterReading(1, sizeSort)
        val val1 = mkBv(1)

        val ref2 = mkRegisterReading(2, addressSort)
        val idx2 = mkRegisterReading(3, sizeSort)
        val val2 = mkBv(2)

        val region = emptyInputArrayRegion(valueArrayDescr, bv32Sort)
            .write(ref1 to idx1, val1, trueExpr)
            .write(ref2 to idx2, val2, trueExpr)

        val concreteRef = heap.allocate()


        val keyConverter = UInputToAllocatedKeyConverter(ref1 to mkBv(0), concreteRef to mkBv(0), mkBv(5))
        val concreteRegion = emptyAllocatedArrayRegion(valueArrayDescr, concreteRef.address, bv32Sort)
            .copyRange(region, mkBv(0), mkBv(5), keyConverter, trueExpr)

        val idx = mkRegisterReading(4, sizeSort)
        val reading = concreteRegion.read(idx)


        val key = region.regionId.keyMapper(translator)(keyConverter.convert(translator.translate(idx)))
        val innerReading =
            translator.translateRegionReading(region, key)
        val guard =
            translator.translate((mkBvSignedLessOrEqualExpr(mkBv(0), idx)) and mkBvSignedLessOrEqualExpr(idx, mkBv(5)))
        val expected = mkIte(guard, innerReading, mkBv(0))

        val translated = translator.translate(reading)

        // due to KSMT non-deterministic with reorderings, we have to check it with solver
        val solver = KZ3Solver(this)
        solver.assert(expected neq translated)
        val status = solver.check()

        assertSame(KSolverStatus.UNSAT, status)
    }

    @Test
    fun testTranslateInputFieldArray() = with(ctx) {
        val ref1 = mkRegisterReading(1, addressSort)
        val ref2 = mkRegisterReading(2, addressSort)
        val ref3 = mkRegisterReading(3, addressSort)

        val g1 = mkRegisterReading(-1, boolSort)
        val g2 = mkRegisterReading(-2, boolSort)
        val g3 = mkRegisterReading(-3, boolSort)

        val region = emptyInputFieldRegion(mockk<Field>(), bv32Sort)
            .write(ref1, mkBv(1), g1)
            .write(ref2, mkBv(2), g2)
            .write(ref3, mkBv(3), g3)

        val ref0 = mkRegisterReading(0, addressSort)
        val reading = region.read(ref0)

        val ref0Eq1Or2Or3 = (ref0 eq ref1) or (ref0 eq ref2) or (ref0 eq ref3)
        val readingNeq123 = (reading neq mkBv(1)) and (reading neq mkBv(2)) and (reading neq mkBv(3))
        val expr = ref0Eq1Or2Or3 and readingNeq123

        val translated = translator.translate(expr and g1 and g2 and g3)

        val solver = KZ3Solver(this)
        solver.assert(translated)
        assertSame(KSolverStatus.UNSAT, solver.check())
    }

    @Test
    fun testTranslateInputArrayLengthArray() = with(ctx) {
        val ref1 = mkRegisterReading(1, addressSort)
        val ref2 = mkRegisterReading(2, addressSort)
        val ref3 = mkRegisterReading(3, addressSort)

        val region = emptyInputArrayLengthRegion(mockk<Field>(), bv32Sort)
            .write(ref1, mkBv(1), trueExpr)
            .write(ref2, mkBv(2), trueExpr)
            .write(ref3, mkBv(3), trueExpr)

        val ref0 = mkRegisterReading(0, addressSort)
        val reading = region.read(ref0)

        val ref0Eq1Or2Or3 = (ref0 eq ref1) or (ref0 eq ref2) or (ref0 eq ref3)
        val readingNeq123 = (reading neq mkBv(1)) and (reading neq mkBv(2)) and (reading neq mkBv(3))
        val expr = ref0Eq1Or2Or3 and readingNeq123

        val translated = translator.translate(expr)

        val solver = KZ3Solver(this)
        solver.assert(translated)
        assertSame(KSolverStatus.UNSAT, solver.check())
    }

    @Test
    fun testTranslateInputToInputArrayCopy() = with(ctx) {

        val ref1 = mkRegisterReading(0, addressSort)
        val idx1 = mkRegisterReading(1, sizeSort)
        val val1 = mkBv(1)

        val ref2 = mkRegisterReading(2, addressSort)
        val idx2 = mkRegisterReading(3, sizeSort)
        val val2 = mkBv(2)

        val inputRegion1 = emptyInputArrayRegion(valueArrayDescr, bv32Sort)
            .write(ref1 to idx1, val1, trueExpr)
            .write(ref2 to idx2, val2, trueExpr)


        val keyConverter = UInputToInputKeyConverter(ref1 to mkBv(0), ref1 to mkBv(0), mkBv(5))
        var inputRegion2 = emptyInputArrayRegion(mockk<Type>(), bv32Sort)

        val idx = mkRegisterReading(4, sizeSort)
        val reading1 = inputRegion2.read(ref2 to idx)

        inputRegion2 = inputRegion2
            .copyRange(inputRegion1, ref1 to mkBv(0), ref1 to mkBv(5), keyConverter, trueExpr)

        val reading2 = inputRegion2.read(ref2 to idx)

        val expr = (reading1 neq reading2) and (ref1 neq ref2)
        val translated = translator.translate(expr)

        val solver = KZ3Solver(this)
        solver.assert(translated)
        val status = solver.check()

        assertSame(KSolverStatus.UNSAT, status)
    }
}