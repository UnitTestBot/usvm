package org.usvm.solver

import io.ksmt.expr.KExpr
import io.ksmt.solver.KSolverStatus
import io.ksmt.solver.z3.KZ3Solver
import io.ksmt.sort.KArraySort
import io.ksmt.sort.KSort
import io.ksmt.utils.mkConst
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
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USizeSort
import org.usvm.api.allocateConcreteRef
import org.usvm.api.readArrayIndex
import org.usvm.api.writeArrayIndex
import org.usvm.collection.array.UAllocatedArrayId
import org.usvm.collection.array.UInputArrayId
import org.usvm.collection.array.USymbolicArrayAllocatedToAllocatedCopyAdapter
import org.usvm.collection.array.USymbolicArrayIndexKeyInfo
import org.usvm.collection.array.USymbolicArrayInputToAllocatedCopyAdapter
import org.usvm.collection.array.USymbolicArrayInputToInputCopyAdapter
import org.usvm.collection.array.length.UInputArrayLengthId
import org.usvm.collection.field.UInputFieldId
import org.usvm.collection.map.ref.URefMapEntryLValue
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.memory.UMemory
import org.usvm.mkSizeExpr
import org.usvm.memory.key.USizeExprKeyInfo
import org.usvm.sizeSort
import kotlin.test.assertEquals
import kotlin.test.assertSame

class TranslationTest {
    private lateinit var ctx: RecordingCtx
    private lateinit var ownership: MutabilityOwnership
    private lateinit var heap: UMemory<Type, Any>
    private lateinit var translator: UExprTranslator<Type, *>

    private lateinit var valueFieldDescr: Pair<Field, UBv32Sort>
    private lateinit var addressFieldDescr: Pair<Field, UAddressSort>
    private lateinit var valueArrayDescr: Type
    private lateinit var addressArrayDescr: Type

    class RecordingCtx(components: UComponents<Type, USizeSort>) : UContext<USizeSort>(components) {
        var storeCallCounter = 0
            private set

        override fun <D : KSort, R : KSort> mkArrayStore(
            array: KExpr<KArraySort<D, R>>,
            index: KExpr<D>,
            value: KExpr<R>
        ): KExpr<KArraySort<D, R>> {
            storeCallCounter++
            return super.mkArrayStore(array, index, value)
        }
    }

    @BeforeEach
    fun initializeContext() {
        val components: UComponents<Type, USizeSort> = mockk()
        every { components.mkTypeSystem(any()) } returns mockk()

        ctx = RecordingCtx(components)
        ownership = MutabilityOwnership()
        every { components.mkSizeExprProvider(any()) } answers { UBv32SizeExprProvider(ctx) }
        heap = UMemory(ctx, ownership, mockk())
        translator = UExprTranslator(ctx)

        valueFieldDescr = mockk<Field>() to ctx.bv32Sort
        addressFieldDescr = mockk<Field>() to ctx.addressSort
        valueArrayDescr = mockk()
        addressArrayDescr = mockk()
    }

    @Test
    fun testTranslateConstAddressSort() = with(ctx) {
        val ref = allocateConcreteRef()
        val idx = mkRegisterReading(0, sizeSort)

        val expr = heap.readArrayIndex(ref, idx, addressArrayDescr, addressSort)
        val translated = translator.translate(expr)

        assertSame(translator.translate(nullRef), translated)
    }

    @Test
    fun testTranslateConstValueSort() = with(ctx) {
        val ref = allocateConcreteRef()
        val idx = mkRegisterReading(0, sizeSort)

        val expr = heap.readArrayIndex(ref, idx, valueArrayDescr, bv32Sort)
        val translated = translator.translate(expr)

        assertSame(mkBv(0), translated)
    }

    @Test
    fun testTranslateWritingsToAllocatedArray() = with(ctx) {
        val ref = allocateConcreteRef()
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


        val region = UInputArrayId<_, _, USizeSort>(valueArrayDescr, bv32Sort)
            .emptyRegion()
            .write(ref1 to idx1, val1, trueExpr, ownership)
            .write(ref2 to idx2, val2, trueExpr, ownership)

        val ref3 = mkRegisterReading(4, addressSort)
        val idx3 = mkRegisterReading(5, sizeSort)

        val reading = region.read(ref3 to idx3)

        val translated = translator.translate(reading)

        val expected = mkArraySort(addressSort, sizeSort, bv32Sort)
            .mkConst(region.collectionId.toString())
            .store(translator.translate(ref1), translator.translate(idx1), val1)
            .store(translator.translate(ref2), translator.translate(idx2), val2)
            .select(translator.translate(ref3), translator.translate(idx3))

        assertSame(expected, translated)
    }

    @Test
    fun testTranslateInputToAllocatedArrayCopy() = with(ctx) {
        val ref1 = mkRegisterReading(0, addressSort) as UHeapRef
        val idx1 = mkRegisterReading(1, sizeSort)
        val val1 = mkBv(1)

        val ref2 = mkRegisterReading(2, addressSort)
        val idx2 = mkRegisterReading(3, sizeSort)
        val val2 = mkBv(2)

        val region = UInputArrayId<_, _, USizeSort>(valueArrayDescr, bv32Sort)
            .emptyRegion()
            .write(ref1 to idx1, val1, trueExpr, ownership)
            .write(ref2 to idx2, val2, trueExpr, ownership)

        val concreteRef = allocateConcreteRef()


        val adapter = USymbolicArrayInputToAllocatedCopyAdapter(
            ref1 to mkSizeExpr(0),
            mkSizeExpr(0),
            mkSizeExpr(5),
            USizeExprKeyInfo()
        )

        val concreteRegion = UAllocatedArrayId<_, _, USizeSort>(valueArrayDescr, bv32Sort, concreteRef.address)
            .emptyRegion()
            .copyRange(region, adapter, trueExpr)

        val idx = mkRegisterReading(4, sizeSort)
        val reading = concreteRegion.read(idx)


        val keyInfo = region.collectionId.keyInfo()
        val key = keyInfo.mapKey(adapter.convert(translator.translate(idx), composer = null), translator)
        val innerReading =
            translator.translate(region.read(key))
        val guard =
            translator.translate((mkBvSignedLessOrEqualExpr(mkBv(0), idx)) and mkBvSignedLessOrEqualExpr(idx, mkBv(5)))
        val expected = mkIte(guard, innerReading, mkBv(0))

        val translated = translator.translate(reading)

        // due to KSMT non-deterministic with reorderings, we have to check it with solver
        KZ3Solver(this).use { solver ->
            solver.assert(expected neq translated)
            val status = solver.check()

            assertSame(KSolverStatus.UNSAT, status)
        }
    }

    @Test
    fun testTranslateInputFieldArray() = with(ctx) {
        val ref1 = mkRegisterReading(1, addressSort)
        val ref2 = mkRegisterReading(2, addressSort)
        val ref3 = mkRegisterReading(3, addressSort)

        val g1 = mkRegisterReading(-1, boolSort)
        val g2 = mkRegisterReading(-2, boolSort)
        val g3 = mkRegisterReading(-3, boolSort)

        val region = UInputFieldId(mockk<Field>(), bv32Sort)
            .emptyRegion()
            .write(ref1, mkBv(1), g1, ownership)
            .write(ref2, mkBv(2), g2, ownership)
            .write(ref3, mkBv(3), g3, ownership)

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

        val region = UInputArrayLengthId(mockk<Field>(), bv32Sort)
            .emptyRegion()
            .write(ref1, mkBv(1), trueExpr, ownership)
            .write(ref2, mkBv(2), trueExpr, ownership)
            .write(ref3, mkBv(3), trueExpr, ownership)

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
        val ref1 = mkRegisterReading(0, addressSort) as UHeapRef
        val idx1 = mkRegisterReading(1, sizeSort)
        val val1 = mkBv(1)

        val ref2 = mkRegisterReading(2, addressSort)
        val idx2 = mkRegisterReading(3, sizeSort)
        val val2 = mkBv(2)

        val inputRegion1 = UInputArrayId<_, _, USizeSort>(valueArrayDescr, bv32Sort)
            .emptyRegion()
            .write(ref1 to idx1, val1, trueExpr, ownership)
            .write(ref2 to idx2, val2, trueExpr, ownership)


        val adapter = USymbolicArrayInputToInputCopyAdapter(
            ref1 to mkSizeExpr(0),
            ref1 to mkSizeExpr(0),
            ref1 to mkSizeExpr(5),
            USymbolicArrayIndexKeyInfo()
        )

        var inputRegion2 = UInputArrayId<_, _, USizeSort>(valueArrayDescr, bv32Sort).emptyRegion()

        val idx = mkRegisterReading(4, sizeSort)
        val reading1 = inputRegion2.read(ref2 to idx)

        inputRegion2 = inputRegion2
            .copyRange(inputRegion1, adapter, trueExpr)

        val reading2 = inputRegion2.read(ref2 to idx)

        val expr = (reading1 neq reading2) and (ref1 neq ref2)
        val translated = translator.translate(expr)

        KZ3Solver(this).use { solver ->
            solver.assert(translated)
            val status = solver.check()

            assertSame(KSolverStatus.UNSAT, status)
        }
    }

    @Test
    fun testSymbolicMapRefKeyRead() = with(ctx) {
        val concreteMapRef = allocateConcreteRef()
        val symbolicMapRef = mkRegisterReading(20, addressSort)

        runSymbolicMapRefKeyReadChecks(concreteMapRef)
        runSymbolicMapRefKeyReadChecks(symbolicMapRef)
    }

    private fun runSymbolicMapRefKeyReadChecks(mapRef: UHeapRef) = with(ctx) {
        val otherConcreteMapRef = allocateConcreteRef()
        val otherSymbolicMapRef = mkRegisterReading(10, addressSort)

        val concreteRef0 = allocateConcreteRef()
        val concreteRef1 = allocateConcreteRef()
        val concreteRefMissed = allocateConcreteRef()

        val symbolicRef0 = mkRegisterReading(0, addressSort)
        val symbolicRef1 = mkRegisterReading(1, addressSort)
        val symbolicRefMissed = mkRegisterReading(2, addressSort)

        var storedValue = 1
        for (ref in listOf(mapRef, otherConcreteMapRef, otherSymbolicMapRef)) {
            for (keyRef in listOf(concreteRef0, concreteRef1, symbolicRef0, symbolicRef1)) {
                val lValue = URefMapEntryLValue(valueFieldDescr.second, ref, keyRef, valueArrayDescr)
                heap.write(lValue, mkBv(storedValue++), trueExpr)
            }
        }

        val concreteValue = heap.read(
            URefMapEntryLValue(valueFieldDescr.second, mapRef, concreteRef0, valueArrayDescr)
        )

        val concreteMissed = heap.read(
            URefMapEntryLValue(valueFieldDescr.second, mapRef, concreteRefMissed, valueArrayDescr)
        )

        val symbolicValue = heap.read(
            URefMapEntryLValue(valueFieldDescr.second, mapRef, symbolicRef0, valueArrayDescr)
        )

        val symbolicMissed = heap.read(
            URefMapEntryLValue(valueFieldDescr.second, mapRef, symbolicRefMissed, valueArrayDescr)
        )

        checkNoConcreteHeapRefs(concreteValue)
        checkNoConcreteHeapRefs(concreteMissed)
        checkNoConcreteHeapRefs(symbolicValue)
        checkNoConcreteHeapRefs(symbolicMissed)
    }

    private fun checkNoConcreteHeapRefs(expr: UExpr<*>) {
        // Translator throws exception if concrete ref occurs
        translator.translate(expr)
    }

    @Test
    fun testTranslateInputToInputArrayCopyAddressSort() = with(ctx) {
        val ref1 = mkRegisterReading(0, addressSort) as UHeapRef
        val idx1 = mkRegisterReading(1, sizeSort)
        val val1 = mkConcreteHeapRef(1)

        val ref2 = mkRegisterReading(2, addressSort)
        val idx2 = mkRegisterReading(3, sizeSort)
        val val2 = mkRegisterReading(5, addressSort)

        val inputRegion1 = UInputArrayId<_, _, USizeSort>(valueArrayDescr, addressSort)
            .emptyRegion()
            .write(ref1 to idx1, val1, trueExpr, ownership)
            .write(ref2 to idx2, val2, trueExpr, ownership)

        val adapter = USymbolicArrayInputToInputCopyAdapter(
            ref1 to mkSizeExpr(0),
            ref1 to mkSizeExpr(0),
            ref1 to mkSizeExpr(5),
            USymbolicArrayIndexKeyInfo()
        )

        var inputRegion2 = UInputArrayId<_, _, USizeSort>(valueArrayDescr, addressSort).emptyRegion()

        val idx = mkRegisterReading(4, sizeSort)
        val reading1 = inputRegion2.read(ref2 to idx)

        inputRegion2 = inputRegion2
            .copyRange(inputRegion1, adapter, trueExpr)

        val reading2 = inputRegion2.read(ref2 to idx)

        val expr = (reading1 neq reading2) and (ref1 neq ref2)
        val translated = translator.translate(expr)

        KZ3Solver(this).use { solver ->
            solver.assert(translated)
            val status = solver.check()

            assertSame(KSolverStatus.UNSAT, status)
        }
    }

    @Test
    fun testTranslateAllocatedToAllocatedArrayCopyAddressSort() = with(ctx) {
        val idx1 = mkRegisterReading(1, sizeSort)
        val val1 = mkConcreteHeapRef(1)

        val idx2 = mkRegisterReading(3, sizeSort)
        val val2 = mkRegisterReading(5, addressSort)

        val allocatedRegion1 = UAllocatedArrayId<_, _, USizeSort>(valueArrayDescr, addressSort, 1)
            .emptyRegion()
            .write(idx1, val1, trueExpr, ownership)
            .write(idx2, val2, trueExpr, ownership)

        val adapter = USymbolicArrayAllocatedToAllocatedCopyAdapter(
            mkSizeExpr(0), mkSizeExpr(0), mkSizeExpr(5), USizeExprKeyInfo()
        )

        var allocatedRegion2 = UAllocatedArrayId<_, _, USizeSort>(valueArrayDescr, addressSort, 2)
            .emptyRegion()

        val idx = mkRegisterReading(4, sizeSort)
        val readingBeforeCopy = allocatedRegion2.read(idx)

        allocatedRegion2 = allocatedRegion2
            .copyRange(allocatedRegion1, adapter, trueExpr)

        val readingAfterCopy = allocatedRegion2.read(idx)

        val outsideOfCopy = mkBvSignedLessExpr(idx, mkBv(0)) or mkBvSignedLessExpr(mkBv(5), idx)
        val expr = (readingBeforeCopy neq readingAfterCopy) and outsideOfCopy
        val translated = translator.translate(expr)

        KZ3Solver(this).use { solver ->
            solver.assert(translated)
            val status = solver.check()

            assertSame(KSolverStatus.UNSAT, status)
        }
    }

    @Test
    fun testCachingOfTranslatedMemoryUpdates() = with(ctx) {
        val allocatedRegion = UAllocatedArrayId<_, _, USizeSort>(valueArrayDescr, sizeSort, 0)
            .emptyRegion()
            .write(mkRegisterReading(0, sizeSort), mkBv(0), trueExpr, ownership)
            .write(mkRegisterReading(1, sizeSort), mkBv(1), trueExpr, ownership)

        val allocatedRegionExtended = allocatedRegion
            .write(mkRegisterReading(2, sizeSort), mkBv(2), trueExpr, ownership)
            .write(mkRegisterReading(3, sizeSort), mkBv(3), trueExpr, ownership)

        val reading = allocatedRegion.read(mkRegisterReading(4, sizeSort))
        val readingExtended = allocatedRegionExtended.read(mkRegisterReading(5, sizeSort))

        translator.translate(reading)

        assertEquals(2, ctx.storeCallCounter)

        translator.translate(readingExtended)

        assertEquals(4, ctx.storeCallCounter)
    }
}
