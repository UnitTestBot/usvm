package org.usvm

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.ksmt.solver.z3.KZ3Solver
import kotlin.test.assertFalse
import kotlin.test.assertIs

class ModelDecodingTest {
    private lateinit var ctx: UContext
    private lateinit var solver: USolverBase<Field, Type, Method>

    private lateinit var memory: UMemoryBase<Field, Type, Method>
    private lateinit var stack: URegistersStack
    private lateinit var heap: URegionHeap<Field, Type>
    private lateinit var mocker: UIndexedMocker<Method>

    @BeforeEach
    fun initializeContext() {
        ctx = UContext()
        val (translator, decoder) = buildTranslatorAndLazyDecoder<Field, Type, Method>(ctx)
        solver = USolverBase(ctx, KZ3Solver(ctx), translator, decoder)

        stack = URegistersStack(ctx)
        stack.push(10)
        heap = URegionHeap(ctx)
        mocker = UIndexedMocker(ctx)

        memory = UMemoryBase(ctx, mockk(), stack, heap, mockk(), mocker)
    }

    @Test
    fun testSmoke(): Unit = with(ctx) {
        val status = solver.check(memory, UPathConstraintsSet(trueExpr))
        assertIs<USolverSat<UModelBase<*, *>>>(status)
    }

    @Test
    fun testSimpleWritingToFields() = with(ctx) {
        val field = mockk<Field>()

        val concreteRef = heap.allocate()
        val symbolicRef0 = stack.readRegister(0, addressSort)
        val symbolicRef1 = stack.readRegister(1, addressSort)

        heap.writeField(concreteRef, field, bv32Sort, mkBv(1), trueExpr)
        heap.writeField(symbolicRef0, field, bv32Sort, mkBv(2), trueExpr)

        val pc = heap.readField(symbolicRef1, field, bv32Sort) eq mkBv(42)

        val status = solver.check(memory, UPathConstraintsSet(pc))
        val model = assertIs<USolverSat<UModelBase<Field, Type>>>(status).model

        val expr = heap.readField(symbolicRef1, field, bv32Sort)

        assertSame(mkBv(42), model.eval(expr))
    }

    @Test
    fun testSimpleWritingToAddressFields() = with(ctx) {
        val field = mockk<Field>()

        val concreteRef = heap.allocate()
        val symbolicRef0 = stack.readRegister(0, addressSort)
        val symbolicRef1 = stack.readRegister(1, addressSort)

        heap.writeField(concreteRef, field, addressSort, symbolicRef1, trueExpr)
        heap.writeField(symbolicRef0, field, addressSort, symbolicRef0, trueExpr)

        val pc = (symbolicRef1 neq nullRef) and (symbolicRef0 neq nullRef) and
                (heap.readField(symbolicRef0, field, addressSort) eq symbolicRef1)

        val status = solver.check(memory, UPathConstraintsSet(pc))
        val model = assertIs<USolverSat<UModelBase<Field, Type>>>(status).model

        val expr = heap.readField(symbolicRef1, field, addressSort)

        assertSame(model.eval(symbolicRef0), model.eval(expr))
    }

    @Test
    fun testSimpleMock() = with(ctx) {
        val field = mockk<Field>()
        val method = mockk<Method>()

        val mockedValue = mocker.call(method, emptySequence(), addressSort).first
        val ref1 = heap.readField(mockedValue, field, addressSort)
        heap.writeField(ref1, field, addressSort, heap.allocate(), trueExpr)
        val ref2 = heap.readField(mockedValue, field, addressSort)

        val pc = (ref1 neq ref2) and (mockedValue neq nullRef) and (ref1 neq nullRef)

        val status = solver.check(memory, UPathConstraintsSet(pc))
        val model = assertIs<USolverSat<UModelBase<Field, Type>>>(status).model

        val mockedValueEqualsRef1 = mockedValue eq ref1

        assertSame(trueExpr, model.eval(mockedValueEqualsRef1))
    }

    @Test
    fun testSimpleMockUnsat(): Unit = with(ctx) {
        val field = mockk<Field>()
        val method = mockk<Method>()

        val mockedValue = mocker.call(method, emptySequence(), addressSort).first
        val ref1 = heap.readField(mockedValue, field, addressSort)
        heap.writeField(ref1, field, addressSort, ref1, trueExpr)
        val ref2 = heap.readField(mockedValue, field, addressSort)

        val pc = (ref1 neq ref2) and (mockedValue neq nullRef) and (ref1 neq nullRef)

        val status = solver.check(memory, UPathConstraintsSet(pc))
        assertIs<USolverUnsat<UModelBase<Field, Type>>>(status)
    }

    @Test
    fun testSimpleSeveralWritingsToArray() = with(ctx) {
        val array = mockk<Type>()

        val concreteRef = heap.allocate()
        val symbolicRef0 = stack.readRegister(0, addressSort)
        val symbolicRef1 = stack.readRegister(1, addressSort)
        val symbolicRef2 = stack.readRegister(2, addressSort)

        val concreteIdx = mkBv(3)
        val idx = stack.readRegister(3, bv32Sort)

        heap.writeArrayIndex(concreteRef, concreteIdx, array, addressSort, symbolicRef1, trueExpr)
        val readedRef = heap.readArrayIndex(concreteRef, idx, array, addressSort)

        val readedRef1 = heap.readArrayIndex(symbolicRef2, idx, array, addressSort)

        heap.writeArrayIndex(readedRef, idx, array, addressSort, symbolicRef0, trueExpr)

        val readedRef2 = heap.readArrayIndex(symbolicRef2, idx, array, addressSort)

        val pc = (symbolicRef2 neq nullRef) and (readedRef1 neq readedRef2)

        val status = solver.check(memory, UPathConstraintsSet(pc))
        val model = assertIs<USolverSat<UModelBase<Field, Type>>>(status).model

        assertSame(model.eval(symbolicRef1), model.eval(symbolicRef2))
    }

    @Test
    fun testLoopedWritingsToArray() = with(ctx) {
        val array = mockk<Type>()

        val symbolicRef0 = stack.readRegister(0, addressSort)
        val symbolicRef1 = stack.readRegister(1, addressSort)
        val symbolicRef2 = stack.readRegister(2, addressSort)

        val concreteIdx = mkBv(3)

        heap.writeArrayIndex(symbolicRef0, concreteIdx, array, addressSort, symbolicRef1, trueExpr)
        heap.writeArrayIndex(symbolicRef1, concreteIdx, array, addressSort, symbolicRef2, trueExpr)
        heap.writeArrayIndex(symbolicRef2, concreteIdx, array, addressSort, symbolicRef0, trueExpr)

        val readedRef = heap.readArrayIndex(symbolicRef0, concreteIdx, array, addressSort)
        val pc = (symbolicRef0 neq nullRef) and (symbolicRef1  neq nullRef) and (symbolicRef2 neq nullRef) and
                (readedRef neq symbolicRef1) and (symbolicRef0 eq symbolicRef1)

        val status = solver.check(memory, UPathConstraintsSet(pc))
        val model = assertIs<USolverSat<UModelBase<Field, Type>>>(status).model

        assertSame(falseExpr, model.eval(symbolicRef2 eq symbolicRef0))
        assertSame(model.eval(readedRef), model.eval(symbolicRef2))
    }
}
