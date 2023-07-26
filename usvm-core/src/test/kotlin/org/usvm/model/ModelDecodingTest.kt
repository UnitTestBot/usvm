package org.usvm.model

import io.ksmt.solver.z3.KZ3Solver
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.usvm.Field
import org.usvm.Method
import org.usvm.UArrayIndexLValue
import org.usvm.UComponents
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UIndexedMocker
import org.usvm.URegisterLValue
import org.usvm.constraints.UPathConstraints
import org.usvm.memory.URegionHeap
import org.usvm.memory.URegistersStack
import org.usvm.solver.USatResult
import org.usvm.solver.USoftConstraintsProvider
import org.usvm.solver.USolverBase
import org.usvm.solver.UTypeSolver
import org.usvm.solver.UUnsatResult
import org.usvm.types.single.SingleTypeSystem
import kotlin.test.assertIs

private typealias Type = SingleTypeSystem.SingleType

class ModelDecodingTest {
    private lateinit var ctx: UContext
    private lateinit var solver: USolverBase<Field, Type, Method>

    private lateinit var pc: UPathConstraints<Type>
    private lateinit var stack: URegistersStack
    private lateinit var heap: URegionHeap<Field, Type>
    private lateinit var mocker: UIndexedMocker<Method>

    @BeforeEach
    fun initializeContext() {
        val components: UComponents<*, *, *> = mockk()
        every { components.mkTypeSystem(any()) } returns SingleTypeSystem

        ctx = UContext(components)
        val softConstraintsProvider = USoftConstraintsProvider<Field, Type>(ctx)
        val (translator, decoder) = buildTranslatorAndLazyDecoder<Field, Type, Method>(ctx)
        val typeSolver = UTypeSolver(SingleTypeSystem)
        solver = USolverBase(ctx, KZ3Solver(ctx), typeSolver, translator, decoder, softConstraintsProvider)

        stack = URegistersStack()
        stack.push(10)
        heap = URegionHeap(ctx)
        mocker = UIndexedMocker(ctx)

        pc = UPathConstraints(ctx)
    }

    @Test
    fun testSmoke(): Unit = with(ctx) {
        val status = solver.checkWithSoftConstraints(pc)
        assertIs<USatResult<UModelBase<*, *>>>(status)
    }

    @Test
    fun testSimpleWritingToFields() = with(ctx) {
        val field = mockk<Field>()

        val concreteRef = heap.allocate()
        val symbolicRef0 = stack.readRegister(0, addressSort)
        val symbolicRef1 = stack.readRegister(1, addressSort)

        heap.writeField(concreteRef, field, bv32Sort, mkBv(1), trueExpr)
        heap.writeField(symbolicRef0, field, bv32Sort, mkBv(2), trueExpr)

        pc += heap.readField(symbolicRef1, field, bv32Sort) eq mkBv(42)
        pc += mkHeapRefEq(symbolicRef1, nullRef).not()
        pc += mkHeapRefEq(symbolicRef0, nullRef).not()

        val status = solver.checkWithSoftConstraints(pc)
        val model = assertIs<USatResult<UModelBase<Field, Type>>>(status).model
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

        pc += symbolicRef1 neq nullRef
        pc += symbolicRef0 neq nullRef
        pc += heap.readField(symbolicRef0, field, addressSort) eq symbolicRef1

        val status = solver.checkWithSoftConstraints(pc)
        val model = assertIs<USatResult<UModelBase<Field, Type>>>(status).model

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

        pc += ref1 neq ref2
        pc += mockedValue neq nullRef
        pc += ref1 neq nullRef

        val status = solver.checkWithSoftConstraints(pc)
        val model = assertIs<USatResult<UModelBase<Field, Type>>>(status).model

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

        pc += ref1 neq ref2
        pc += mockedValue neq nullRef
        pc += ref1 neq nullRef

        val status = solver.checkWithSoftConstraints(pc)
        assertIs<UUnsatResult<UModelBase<Field, Type>>>(status)
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
        val readRef = heap.readArrayIndex(concreteRef, idx, array, addressSort)

        val readRef1 = heap.readArrayIndex(symbolicRef2, idx, array, addressSort)

        heap.writeArrayIndex(readRef, idx, array, addressSort, symbolicRef0, trueExpr)

        val readRef2 = heap.readArrayIndex(symbolicRef2, idx, array, addressSort)

        pc += (symbolicRef2 neq nullRef) and (readRef1 neq readRef2)

        val status = solver.checkWithSoftConstraints(pc)
        val model = assertIs<USatResult<UModelBase<Field, Type>>>(status).model

        assertSame(model.eval(symbolicRef1), model.eval(symbolicRef2))
    }

    @Test
    fun testLoopWritingsToArray() = with(ctx) {
        val array = mockk<Type>()

        val symbolicRef0 = stack.readRegister(0, addressSort)
        val symbolicRef1 = stack.readRegister(1, addressSort)
        val symbolicRef2 = stack.readRegister(2, addressSort)

        val concreteIdx = mkBv(3)

        heap.writeArrayIndex(symbolicRef0, concreteIdx, array, addressSort, symbolicRef1, trueExpr)
        heap.writeArrayIndex(symbolicRef1, concreteIdx, array, addressSort, symbolicRef2, trueExpr)
        heap.writeArrayIndex(symbolicRef2, concreteIdx, array, addressSort, symbolicRef0, trueExpr)

        val readRef = heap.readArrayIndex(symbolicRef0, concreteIdx, array, addressSort)
        pc += symbolicRef0 neq nullRef
        pc += symbolicRef1 neq nullRef
        pc += symbolicRef2 neq nullRef
        pc += readRef neq symbolicRef1
        pc += symbolicRef0 eq symbolicRef1

        val status = solver.checkWithSoftConstraints(pc)
        val model = assertIs<USatResult<UModelBase<Field, Type>>>(status).model

        assertSame(falseExpr, model.eval(symbolicRef2 eq symbolicRef0))
        assertSame(model.eval(readRef), model.eval(symbolicRef2))
    }

    @Test
    fun testSimpleReadingFromModel() = with(ctx) {
        val array = mockk<Type>()

        val symbolicRef0 = stack.readRegister(0, addressSort)

        val concreteIdx = mkBv(3)

        val readExpr = heap.readArrayIndex(symbolicRef0, concreteIdx, array, bv32Sort)
        pc += symbolicRef0 neq nullRef
        pc += readExpr eq mkBv(42)

        val status = solver.checkWithSoftConstraints(pc)
        val model = assertIs<USatResult<UModelBase<Field, Type>>>(status).model

        val ref = assertIs<UConcreteHeapRef>(model.read(URegisterLValue(addressSort, 0)))
        val expr = model.read(UArrayIndexLValue(bv32Sort, ref, concreteIdx, array))
        assertEquals(mkBv(42), expr)
    }
}
