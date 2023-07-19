package org.usvm.types

import io.ksmt.solver.z3.KZ3Solver
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.usvm.Field
import org.usvm.INITIAL_INPUT_ADDRESS
import org.usvm.Method
import org.usvm.NULL_ADDRESS
import org.usvm.UComponents
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.constraints.UPathConstraints
import org.usvm.memory.UMemoryBase
import org.usvm.memory.URegionHeap
import org.usvm.memory.emptyInputArrayRegion
import org.usvm.memory.heapRefEq
import org.usvm.model.UModelBase
import org.usvm.model.buildTranslatorAndLazyDecoder
import org.usvm.solver.TypeSolverQuery
import org.usvm.solver.USatResult
import org.usvm.solver.USoftConstraintsProvider
import org.usvm.solver.USolverBase
import org.usvm.solver.UTypeSolver
import org.usvm.solver.UTypeUnsatResult
import org.usvm.solver.UUnsatResult
import org.usvm.types.system.TestType
import org.usvm.types.system.a
import org.usvm.types.system.b
import org.usvm.types.system.base1
import org.usvm.types.system.base2
import org.usvm.types.system.c
import org.usvm.types.system.derived1A
import org.usvm.types.system.derived1B
import org.usvm.types.system.derivedMulti
import org.usvm.types.system.derivedMultiInterfaces
import org.usvm.types.system.interface1
import org.usvm.types.system.interface2
import org.usvm.types.system.interfaceAB
import org.usvm.types.system.interfaceAC
import org.usvm.types.system.interfaceBC1
import org.usvm.types.system.interfaceBC2
import org.usvm.types.system.testTypeSystem
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TypeSolverTest {
    private val typeSystem = testTypeSystem
    private val components = mockk<UComponents<Field, TestType, Method>>()
    private val ctx = UContext(components)
    private val solver: USolverBase<Field, TestType, Method>
    private val typeSolver: UTypeSolver<Field, TestType>

    init {
        val (translator, decoder) = buildTranslatorAndLazyDecoder<Field, TestType, Method>(ctx)
        val softConstraintsProvider = USoftConstraintsProvider<Field, TestType>(ctx)

        typeSolver = UTypeSolver(translator, typeSystem)
        solver = USolverBase(ctx, KZ3Solver(ctx), typeSolver, translator, decoder, softConstraintsProvider)

        every { components.mkSolver(ctx) } returns solver
        every { components.mkTypeSystem(ctx) } returns typeSystem
    }

    private val pc = UPathConstraints<TestType>(ctx)
    private val memory = UMemoryBase<Field, TestType, Method>(ctx, pc.typeConstraints)

    @Test
    fun `Test concrete ref -- open type inheritance`() {
        val ref = memory.alloc(base1)
        val types = memory.typeStreamOf(ref)
        types.take100AndAssertEqualsToSetOf(base1)
    }

    @Test
    fun `Test symbolic ref -- open type inheritance`() = with(ctx) {
        val ref = ctx.mkRegisterReading(0, addressSort)
        pc += mkIsExpr(ref, base1)
        pc += mkHeapRefEq(ref, nullRef).not()
        val model = (solver.check(pc) as USatResult<UModelBase<Field, TestType>>).model
        val concreteRef = assertIs<UConcreteHeapRef>(model.eval(ref))
        val types = model.typeStreamOf(concreteRef)
        types.take100AndAssertEqualsToSetOf(base1, derived1A, derived1B)
    }

    @Test
    fun `Test symbolic ref -- interface type inheritance`() = with(ctx) {
        val ref = ctx.mkRegisterReading(0, addressSort)
        pc += mkIsExpr(ref, interface1)
        pc += mkHeapRefEq(ref, nullRef).not()
        val model = (solver.check(pc) as USatResult<UModelBase<Field, TestType>>).model
        val concreteRef = assertIs<UConcreteHeapRef>(model.eval(ref))
        val types = model.typeStreamOf(concreteRef)
        types.take100AndAssertEqualsToSetOf(derived1A, derivedMulti, derivedMultiInterfaces)
    }

    @Test
    fun `Test concrete ref -- empty intersection simplification`() = with(ctx) {
        val ref = memory.alloc(base1)
        pc += mkIsExpr(ref, base2)
        assertTrue(pc.isFalse)
    }

    @Test
    fun `Test symbolic ref -- empty intersection simplification`() = with(ctx) {
        val ref = ctx.mkRegisterReading(0, addressSort)
        pc += mkIsExpr(ref, base1)
        pc += mkIsExpr(ref, base2)
        pc += mkHeapRefEq(ref, nullRef).not()
        assertTrue(pc.isFalse)
    }

    @Test
    fun `Test symbolic ref cast -- empty intersection simplification`(): Unit = with(ctx) {
        val ref = ctx.mkRegisterReading(0, addressSort)
        pc += mkIsExpr(ref, base1)
        pc += mkIsExpr(ref, base2)

        val resultWithoutNullConstraint = solver.check(pc)
        assertIs<USatResult<UModelBase<Field, TestType>>>(resultWithoutNullConstraint)

        pc += mkHeapRefEq(ref, nullRef).not()

        val resultWithNullConstraint = solver.check(pc)
        assertIs<UUnsatResult<UModelBase<Field, TestType>>>(resultWithNullConstraint)
    }

    @Test
    fun `Test symbolic ref -- different types`(): Unit = with(ctx) {
        val ref0 = ctx.mkRegisterReading(0, addressSort)
        val ref1 = ctx.mkRegisterReading(1, addressSort)

        pc += mkIsExpr(ref0, base1)
        pc += mkIsExpr(ref1, base2)
        pc += mkHeapRefEq(ref0, nullRef).not()
        pc += mkHeapRefEq(ref1, nullRef).not()

        val resultWithoutEqConstraint = solver.check(pc)
        val model = assertIs<USatResult<UModelBase<Field, TestType>>>(resultWithoutEqConstraint).model
        assertNotEquals(model.eval(ref0), model.eval(ref1))

        pc += mkHeapRefEq(ref0, ref1)

        val resultWithEqConstraint = solver.check(pc)
        assertIs<UUnsatResult<UModelBase<Field, TestType>>>(resultWithEqConstraint)
    }

    @Test
    fun `Test symbolic ref -- different types 3 refs`(): Unit = with(ctx) {
        val ref0 = ctx.mkRegisterReading(0, addressSort)
        val ref1 = ctx.mkRegisterReading(1, addressSort)
        val ref2 = ctx.mkRegisterReading(2, addressSort)

        pc += mkIsExpr(ref0, interfaceAB)
        pc += mkIsExpr(ref1, interfaceBC1)
        pc += mkIsExpr(ref2, interfaceAC)
        pc += mkHeapRefEq(ref0, nullRef).not()
        pc += mkHeapRefEq(ref1, nullRef).not()
        pc += mkHeapRefEq(ref2, nullRef).not()

        val resultWithoutEqConstraint = solver.check(pc)
        val model = assertIs<USatResult<UModelBase<Field, TestType>>>(resultWithoutEqConstraint).model
        assertNotEquals(model.eval(ref0), model.eval(ref1))

        pc += mkHeapRefEq(ref0, ref1)
        pc += mkHeapRefEq(ref1, ref2)

        assertTrue(pc.isFalse)
    }

    @Test
    fun `Test symbolic ref -- different interface types but same base type`(): Unit = with(ctx) {
        val ref0 = ctx.mkRegisterReading(0, addressSort)
        val ref1 = ctx.mkRegisterReading(1, addressSort)

        pc += mkIsExpr(ref0, base1)
        pc += mkIsExpr(ref0, interface1)
        pc += mkIsExpr(ref1, base1)
        pc += mkIsExpr(ref1, interface2)

        val resultWithoutEqConstraint = solver.check(pc)
        val modelWithoutEqConstraint =
            assertIs<USatResult<UModelBase<Field, TestType>>>(resultWithoutEqConstraint).model

        val concreteAddress0 = assertIs<UConcreteHeapRef>(modelWithoutEqConstraint.eval(ref0)).address
        val concreteAddress1 = assertIs<UConcreteHeapRef>(modelWithoutEqConstraint.eval(ref1)).address

        val bothNull = concreteAddress0 == NULL_ADDRESS && concreteAddress1 == NULL_ADDRESS
        assertTrue(bothNull || concreteAddress0 != concreteAddress1)

        pc += mkHeapRefEq(ref0, ref1)

        val resultWithEqConstraint = solver.check(pc)
        val modelWithEqConstraint = assertIs<USatResult<UModelBase<Field, TestType>>>(resultWithEqConstraint).model

        assertEquals(mkConcreteHeapRef(NULL_ADDRESS), modelWithEqConstraint.eval(ref0))
        assertEquals(mkConcreteHeapRef(NULL_ADDRESS), modelWithEqConstraint.eval(ref1))

        pc += mkHeapRefEq(nullRef, ref0).not()

        val resultWithEqAndNotNullConstraint = solver.check(pc)
        assertIs<UUnsatResult<UModelBase<Field, TestType>>>(resultWithEqAndNotNullConstraint)
    }

    @Test
    fun `Test symbolic ref -- expressions to assert correctness`(): Unit = with(ctx) {
        val a = ctx.mkRegisterReading(0, addressSort)
        val b1 = ctx.mkRegisterReading(1, addressSort)
        val b2 = ctx.mkRegisterReading(2, addressSort)
        val c = ctx.mkRegisterReading(3, addressSort)

        pc += mkHeapRefEq(a, nullRef).not() and
            mkHeapRefEq(b1, nullRef).not() and
            mkHeapRefEq(b2, nullRef).not() and
            mkHeapRefEq(c, nullRef).not()

        pc += mkIsExpr(a, interfaceAB)
        pc += mkIsExpr(b1, interfaceBC1)
        pc += mkIsExpr(b2, interfaceBC2)
        pc += mkIsExpr(c, interfaceAC)

        pc += mkHeapRefEq(b1, b2)

        with(pc.clone()) {
            val result = solver.check(this)
            assertIs<USatResult<UModelBase<Field, TestType>>>(result)

            val concreteA = result.model.eval(a)
            val concreteB1 = result.model.eval(b1)
            val concreteB2 = result.model.eval(b2)
            val concreteC = result.model.eval(c)

            assertEquals(concreteB1, concreteB2)
            assertTrue(concreteA != concreteB1 || concreteB1 != concreteC || concreteC != concreteA)
        }

        with(pc.clone()) {
            val model = mockk<UModelBase<Field, TestType>> {
                every { eval(a) } returns mkConcreteHeapRef(INITIAL_INPUT_ADDRESS)
                every { eval(b1) } returns mkConcreteHeapRef(INITIAL_INPUT_ADDRESS)
                every { eval(b2) } returns mkConcreteHeapRef(INITIAL_INPUT_ADDRESS)
                every { eval(c) } returns mkConcreteHeapRef(INITIAL_INPUT_ADDRESS)
            }

            val query = TypeSolverQuery(
                typeConstraints,
                logicalConstraints,
                symbolicToConcrete = { model.eval(it) as UConcreteHeapRef },
                isExprToInterpreted = { error("should not be called") }
            )

            val result = typeSolver.check(query)
            assertIs<UTypeUnsatResult<TestType>>(result)
        }


        with(pc.clone()) {
            this += mkHeapRefEq(a, c) and mkHeapRefEq(b1, c)
            val result = solver.check(this)
            assertIs<UUnsatResult<UModelBase<Field, TestType>>>(result)
        }
    }

    @Test
    fun `Test symbolic ref -- expressions to assert correctness about null -- three equals`(): Unit = with(ctx) {
        val a = ctx.mkRegisterReading(0, addressSort)
        val b = ctx.mkRegisterReading(1, addressSort)
        val c = ctx.mkRegisterReading(2, addressSort)

        pc += mkIsExpr(a, interfaceAB)
        pc += mkIsExpr(b, interfaceBC1)
        pc += mkIsExpr(c, interfaceAC)

        // it's overcomplicated a == c && b == c, so it's not leak to the UEqualityConstraints
        pc += (mkHeapRefEq(a, c) or mkHeapRefEq(b, c)) and (!mkHeapRefEq(a, c) or !mkHeapRefEq(b, c)).not()

        val resultBeforeNotNullConstraints = solver.check(pc)
        val model = assertIs<USatResult<UModelBase<Field, TestType>>>(resultBeforeNotNullConstraints).model

        assertIs<USatResult<UModelBase<Field, TestType>>>(resultBeforeNotNullConstraints)

        val concreteA = assertIs<UConcreteHeapRef>(model.eval(a)).address
        val concreteB = assertIs<UConcreteHeapRef>(model.eval(b)).address
        val concreteC = assertIs<UConcreteHeapRef>(model.eval(c)).address

        assertTrue(concreteA == 0 && concreteB == 0 && concreteC == 0)

        pc += mkOrNoSimplify(mkHeapRefEq(a, nullRef).not(), falseExpr)

        val resultWithNotNullConstraints = solver.check(pc)
        assertIs<UUnsatResult<UModelBase<Field, TestType>>>(resultWithNotNullConstraints)
    }

    @Test
    fun `Test symbolic ref -- expressions to assert correctness about null -- two equals`(): Unit = with(ctx) {
        val a = ctx.mkRegisterReading(0, addressSort)
        val b = ctx.mkRegisterReading(1, addressSort)
        val c = ctx.mkRegisterReading(2, addressSort)

        pc += mkIsExpr(a, interfaceAB)
        pc += mkIsExpr(b, interfaceBC1)
        pc += mkIsExpr(c, interfaceAC)

        // it's overcomplicated a == b, so it's not leak to the UEqualityConstraints
        pc += mkOrNoSimplify(mkHeapRefEq(a, b), falseExpr)

        pc += mkOrNoSimplify(mkHeapRefEq(a, nullRef).not(), falseExpr)

        val result = solver.check(pc)
        val model = assertIs<USatResult<UModelBase<Field, TestType>>>(result).model

        val concreteA = assertIs<UConcreteHeapRef>(model.eval(a)).address
        val concreteB = assertIs<UConcreteHeapRef>(model.eval(b)).address
        val concreteC = assertIs<UConcreteHeapRef>(model.eval(c)).address

        assertTrue(concreteA != 0 && concreteA == concreteB && concreteC == 0)
    }

    @Test
    fun `Symbolic ref -- ite must not occur as refs in type constraints`() = with(ctx) {
        val arr1 = mkRegisterReading(0, addressSort)
        val arr2 = mkRegisterReading(1, addressSort)

        val val1 = memory.alloc(base2)
        val val2 = memory.alloc(base2)

        pc += mkHeapRefEq(arr1, nullRef).not()
        pc += mkHeapRefEq(arr2, nullRef).not()

        val idx1 = 0.toBv()
        val idx2 = 0.toBv()

        val field = mockk<Field>()
        val heap = URegionHeap<Field, TestType>(ctx)

        heap.writeField(val1, field, bv32Sort, 1.toBv(), trueExpr)
        heap.writeField(val2, field, bv32Sort, 2.toBv(), trueExpr)

        val inputRegion = emptyInputArrayRegion(mockk<TestType>(), addressSort)
            .write(arr1 to idx1, val1, trueExpr)
            .write(arr2 to idx2, val2, trueExpr)

        val firstReading = inputRegion.read(arr1 to idx1)
        val secondReading = inputRegion.read(arr2 to idx2)

        pc += mkIsExpr(arr1, base1)
        pc += mkIsExpr(arr2, base1)

        pc += mkHeapRefEq(firstReading, nullRef).not()
        pc += mkHeapRefEq(secondReading, nullRef).not()

        pc += mkIsExpr(firstReading, base2)
        pc += mkIsExpr(secondReading, base2)

        val fstFieldValue = heap.readField(firstReading, field, bv32Sort)
        val sndFieldValue = heap.readField(secondReading, field, bv32Sort)

        pc += fstFieldValue eq sndFieldValue

        val status = solver.check(pc)

        assertTrue { status is USatResult<*> }
    }

    @Test
    fun `Test symbolic ref -- not instance of constraint`(): Unit = with(ctx) {
        val ref = ctx.mkRegisterReading(0, addressSort)

        pc += mkHeapRefEq(ref, nullRef) or mkIsExpr(ref, interfaceAB).not()
        assertIs<USatResult<UModelBase<Field, TestType>>>(solver.check(pc))

        pc += heapRefEq(ref, nullRef).not() and (mkIsExpr(ref, a) or mkIsExpr(ref, b))
        assertIs<UUnsatResult<*>>(solver.check(pc))
    }

    @Test
    fun `Test symbolic ref -- isExpr or bool variable`(): Unit = with(ctx) {
        val ref = ctx.mkRegisterReading(0, addressSort)
        val unboundedBoolean = ctx.mkRegisterReading(1, boolSort)
        pc += mkIsExpr(ref, a) or mkIsExpr(ref, b) or mkIsExpr(ref, c)
        pc += mkIsExpr(ref, interfaceAB) xor unboundedBoolean
        val result1 = solver.check(pc)
        assertIs<USatResult<UModelBase<Field, TestType>>>(result1)
        pc += unboundedBoolean
        val result2 = solver.check(pc)
        val model = assertIs<USatResult<UModelBase<Field, TestType>>>(result2).model
        val concreteA = model.eval(ref) as UConcreteHeapRef
        val types = model.typeStreamOf(concreteA)
        types.take100AndAssertEqualsToSetOf(c)
    }

    @Test
    fun `Test symbolic ref -- ite constraint`(): Unit = with(ctx) {
        val ref1 = ctx.mkRegisterReading(0, addressSort)
        val ref2 = ctx.mkRegisterReading(1, addressSort)

        val unboundedBoolean1 = ctx.mkRegisterReading(2, boolSort)
        val unboundedBoolean2 = ctx.mkRegisterReading(3, boolSort)

        pc += mkHeapRefEq(ref1, nullRef).not()
        pc += mkHeapRefEq(ref2, nullRef).not()

        val iteIsExpr1 = pc.typeConstraints.evalIs(mkIte(unboundedBoolean1, ref1, ref2), base1)
        val iteIsExpr2 = pc.typeConstraints.evalIs(mkIte(unboundedBoolean2, ref1, ref2), base2)

        pc += iteIsExpr1
        pc += iteIsExpr2
        pc += ref1 neq ref2

        val result = solver.check(pc)
        val model = assertIs<USatResult<UModelBase<Field, TestType>>>(result).model

        val concreteA = model.eval(ref1)
        val concreteB = model.eval(ref2)

        assertNotEquals(model.typeStreamOf(concreteA).takeFirst(), model.typeStreamOf(concreteB).takeFirst())
        assertNotEquals(model.eval(unboundedBoolean1), model.eval(unboundedBoolean2))

        pc += unboundedBoolean1 eq unboundedBoolean2

        assertIs<UUnsatResult<*>>(solver.check(pc))
    }

    private fun <T> UTypeStream<T>.take100AndAssertEqualsToSetOf(vararg elements: T) {
        val set = elements.toSet()
        val result = take(100)
        assertEquals(set.size, result.size)
        assertEquals(set, result.toSet())
    }
}