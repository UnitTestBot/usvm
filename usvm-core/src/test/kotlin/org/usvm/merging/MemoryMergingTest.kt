package org.usvm.merging

import io.ksmt.solver.KSolverStatus
import io.ksmt.solver.z3.KZ3Solver
import io.ksmt.utils.uncheckedCast
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.usvm.Method
import org.usvm.UBoolExpr
import org.usvm.UBv32SizeExprProvider
import org.usvm.UBv32Sort
import org.usvm.UComponents
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.api.allocateConcreteRef
import org.usvm.api.readField
import org.usvm.api.writeField
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.constraints.UPathConstraints
import org.usvm.memory.UMemory
import org.usvm.memory.URegisterStackLValue
import org.usvm.solver.UExprTranslator
import org.usvm.types.single.SingleTypeSystem
import org.usvm.types.single.SingleTypeSystem.SingleType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class MemoryMergingTest {
    private lateinit var ctx: UContext<UBv32Sort>
    private lateinit var ownership: MutabilityOwnership
    private lateinit var translator: UExprTranslator<SingleType, *>
    private lateinit var smtSolver: KZ3Solver

    @BeforeEach
    fun initializeContext() {
        val components: UComponents<SingleType, UBv32Sort> = mockk()
        every { components.mkTypeSystem(any()) } returns SingleTypeSystem
        every { components.mkSizeExprProvider(any()) } answers { UBv32SizeExprProvider(ctx) }
        ctx = UContext(components)
        ownership = MutabilityOwnership()
        translator = UExprTranslator(ctx)
        smtSolver = KZ3Solver(ctx)
    }

    @Test
    fun `Empty memory`() = with(ctx) {
        val byCondition = mkConst("cond", boolSort)
        val pathConstraints = UPathConstraints<SingleType>(this, ownership)
        val memoryLeft = UMemory<SingleType, Method>(this, ownership, pathConstraints.typeConstraints)
        val memoryRight = memoryLeft.clone(pathConstraints.typeConstraints, MutabilityOwnership(), MutabilityOwnership())

        checkMergedEqualsToOriginal(
            memoryLeft,
            memoryRight,
            byCondition,
            { it.read(URegisterStackLValue(addressSort, 0)) },
            { it.read(URegisterStackLValue(bv32Sort, 1)) },
            { it.readField(mkConcreteHeapRef(1), Unit, addressSort) },
        )
    }

    @Test
    fun `Distinct stack`() = with(ctx) {
        val byCondition = mkConst("cond", boolSort)
        val pathConstraints = UPathConstraints<SingleType>(this, ownership)

        val memoryLeft = UMemory<SingleType, Method>(this, ownership, pathConstraints.typeConstraints)
        memoryLeft.stack.push(3)
        memoryLeft.stack.writeRegister(0, mkBv(42))
        memoryLeft.stack.writeRegister(1, mkBv(1337))

        val memoryRight = memoryLeft.clone(pathConstraints.typeConstraints, MutabilityOwnership(), MutabilityOwnership())
        memoryRight.stack.writeRegister(0, mkBv(13))
        memoryRight.stack.writeRegister(2, mkBv(9))

        checkMergedEqualsToOriginal(
            memoryLeft,
            memoryRight,
            byCondition,
            { it.stack.readRegister(0, bv32Sort) },
            { it.stack.readRegister(1, bv32Sort) },
            { it.stack.readRegister(2, bv32Sort) },
        )
    }

    @Test
    fun `Distinct regions`(): Unit = with(ctx) {
        assertFails { // TODO: improve memory regions constraints merging
            val byCondition = mkConst("cond", boolSort)
            val pathConstraints = UPathConstraints<SingleType>(this, ownership)

            val leftOwnership = ownership
            val memoryLeft = UMemory<SingleType, Method>(this, leftOwnership, pathConstraints.typeConstraints)

            val ref1 = allocateConcreteRef()
            val ref2 = allocateConcreteRef()
            val ref3 = allocateConcreteRef()

            memoryLeft.writeField(ref1, Unit, addressSort, mkRegisterReading(1, addressSort), trueExpr)
            memoryLeft.writeField(ref2, Unit, addressSort, mkRegisterReading(2, addressSort), trueExpr)
            memoryLeft.writeField(ref3, Unit, addressSort, mkRegisterReading(3, addressSort), trueExpr)

            val memoryRight = memoryLeft.clone(pathConstraints.typeConstraints, MutabilityOwnership(), MutabilityOwnership())
            memoryRight.writeField(ref1, Unit, addressSort, mkRegisterReading(-1, addressSort), trueExpr)
            memoryRight.writeField(ref2, Unit, addressSort, mkRegisterReading(-2, addressSort), trueExpr)
            memoryRight.writeField(ref3, Unit, addressSort, mkRegisterReading(-3, addressSort), trueExpr)

            checkMergedEqualsToOriginal(
                memoryLeft,
                memoryRight,
                byCondition,
                { it.readField(ref1, Unit, addressSort) },
                { it.readField(ref2, Unit, addressSort) },
                { it.readField(ref3, Unit, addressSort) },
            )
        }
    }

    private fun checkMergedEqualsToOriginal(
        memoryLeft: UMemory<SingleType, Method>,
        memoryRight: UMemory<SingleType, Method>,
        byCondition: UBoolExpr,
        vararg getters: (UMemory<SingleType, Method>) -> UExpr<out USort>,
    ) = with(ctx) {
        val mergeGuard = MutableMergeGuard(this).apply { appendThis(sequenceOf(byCondition)) }
        val mergedMemory = checkNotNull(memoryLeft.mergeWith(
            memoryRight, mergeGuard, MutabilityOwnership(), MutabilityOwnership(), MutabilityOwnership()
        ))

        for (getter in getters) {
            val leftExpr: UExpr<USort> = getter(memoryLeft).uncheckedCast()
            val rightExpr: UExpr<USort> = getter(memoryRight).uncheckedCast()
            val mergedExpr: UExpr<USort> = getter(mergedMemory).uncheckedCast()
            val ite = mkIte(byCondition, leftExpr, rightExpr)
            val exprsAreNotEqual = ite neq mergedExpr
            smtSolver.push()
            smtSolver.assert(translator.translate(exprsAreNotEqual))
            val status = smtSolver.check()
            assertEquals(KSolverStatus.UNSAT, status)
            smtSolver.pop()
        }
    }
}
