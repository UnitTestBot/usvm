package org.usvm.types

import io.ksmt.solver.z3.KZ3Solver
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.Field
import org.usvm.Method
import org.usvm.UComponents
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.constraints.UPathConstraints
import org.usvm.memory.UMemoryBase
import org.usvm.model.UModelBase
import org.usvm.model.buildTranslatorAndLazyDecoder
import org.usvm.solver.USatResult
import org.usvm.solver.USoftConstraintsProvider
import org.usvm.solver.USolverBase
import org.usvm.solver.UUnsatResult
import org.usvm.types.system.TestType
import org.usvm.types.system.base1
import org.usvm.types.system.base2
import org.usvm.types.system.interface1
import org.usvm.types.system.interface2
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
    init {
        val (translator, decoder) = buildTranslatorAndLazyDecoder<Field, TestType, Method>(ctx)
        val softConstraintsProvider = USoftConstraintsProvider<Field, TestType>(ctx)

        val smtSolver = KZ3Solver(ctx)
        solver = USolverBase(ctx, smtSolver, translator, decoder, softConstraintsProvider)

        every { components.mkSolver(ctx) } returns solver
        every { components.mkTypeSystem(ctx) } returns typeSystem
    }

    private val pc = UPathConstraints<TestType>(ctx)
    private val memory = UMemoryBase<Field, TestType, Method>(ctx, pc.typeConstraints)

    @Test
    fun `Test concrete ref -- open type inheritance`() {
        val ref = memory.alloc(base1)
        val typeRegion = memory.types.readTypeRegion(ref)
        val type = typeRegion.typeStream.takeFirst()
        assertEquals(base1, type)
    }

    @Test
    fun `Test symbolic ref -- open type inheritance`() = with(ctx) {
        val ref = ctx.mkRegisterReading(0, addressSort)
        pc += mkIsExpr(ref, base1)
        val model = (solver.check(pc, useSoftConstraints = false) as USatResult<UModelBase<Field, TestType>>).model
        val concreteRef = model.eval(ref) as UConcreteHeapRef
        val type = model.types.typeOf(concreteRef.address)
        assertTrue(typeSystem.isSupertype(base1, type))
        assertTrue(typeSystem.isInstantiable(type))
    }

    @Test
    fun `Test symbolic ref -- interface type inheritance`() = with(ctx) {
        val ref = ctx.mkRegisterReading(0, addressSort)
        pc += mkIsExpr(ref, interface1)
        val model = (solver.check(pc, useSoftConstraints = false) as USatResult<UModelBase<Field, TestType>>).model
        val concreteRef = model.eval(ref) as UConcreteHeapRef
        val type = model.types.typeOf(concreteRef.address)
        assertTrue(typeSystem.isSupertype(interface1, type))
        assertTrue(typeSystem.isInstantiable(type))
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
        assertTrue(pc.isFalse)
    }

    @Test
    @Disabled("TODO: can't evaluate complex UIsExpressions now")
    fun `Test symbolic ref cast -- empty intersection simplification`(): Unit = with(ctx) {
        val ref = ctx.mkRegisterReading(0, addressSort)
        pc += (mkIsExpr(ref, base1) or mkHeapRefEq(ref, nullRef))
        pc += (mkIsExpr(ref, base2) or mkHeapRefEq(ref, nullRef))

        val resultWithoutNullConstraint = solver.check(pc, useSoftConstraints = false)
        assertIs<USatResult<UModelBase<Field, TestType>>>(resultWithoutNullConstraint)

        pc += mkHeapRefEq(ref, nullRef).not()

        val resultWithNullConstraint = solver.check(pc, useSoftConstraints = false)
        assertIs<UUnsatResult<UModelBase<Field, TestType>>>(resultWithNullConstraint)
    }

    @Test
    fun `Test symbolic ref -- different types`(): Unit = with(ctx) {
        val ref0 = ctx.mkRegisterReading(0, addressSort)
        val ref1 = ctx.mkRegisterReading(1, addressSort)

        pc += mkIsExpr(ref0, base1)
        pc += mkIsExpr(ref1, base2)

        val resultWithoutEqConstraint = solver.check(pc, useSoftConstraints = false)
        val model = assertIs<USatResult<UModelBase<Field, TestType>>>(resultWithoutEqConstraint).model
        assertNotEquals(model.eval(ref0), model.eval(ref1))

        pc += mkHeapRefEq(ref0, ref1)

        val resultWithEqConstraint = solver.check(pc, useSoftConstraints = false)
        assertIs<UUnsatResult<UModelBase<Field, TestType>>>(resultWithEqConstraint)
    }

    @Test
    fun `Test symbolic ref -- different interface types but same base type`(): Unit = with(ctx) {
        val ref0 = ctx.mkRegisterReading(0, addressSort)
        val ref1 = ctx.mkRegisterReading(1, addressSort)

        pc += mkIsExpr(ref0, base1)
        pc += mkIsExpr(ref0, interface1)
        pc += mkIsExpr(ref1, base1)
        pc += mkIsExpr(ref0, interface2)

        TODO()

        val resultWithoutEqConstraint = solver.check(pc, useSoftConstraints = false)
        val model = assertIs<USatResult<UModelBase<Field, TestType>>>(resultWithoutEqConstraint).model
        assertNotEquals(model.eval(ref0), model.eval(ref1))

        pc += mkHeapRefEq(ref0, ref1)

        val resultWithEqConstraint = solver.check(pc, useSoftConstraints = false)
        assertIs<UUnsatResult<UModelBase<Field, TestType>>>(resultWithEqConstraint)
    }
}