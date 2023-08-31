package org.usvm.api.collections

import io.ksmt.solver.KSolver
import io.ksmt.solver.KSolverStatus
import io.ksmt.solver.z3.KZ3Solver
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.usvm.Type
import org.usvm.UBoolExpr
import org.usvm.UCallStack
import org.usvm.UComponents
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UState
import org.usvm.constraints.UPathConstraints
import org.usvm.memory.UMemory
import org.usvm.model.buildTranslatorAndLazyDecoder
import org.usvm.solver.UExprTranslator
import org.usvm.solver.USoftConstraintsProvider
import org.usvm.solver.USolverBase
import org.usvm.solver.UTypeSolver
import org.usvm.types.single.SingleTypeSystem
import kotlin.test.assertEquals

abstract class SymbolicCollectionTestBase {
    lateinit var ctx: UContext
    lateinit var pathConstraints: UPathConstraints<SingleTypeSystem.SingleType, UContext>
    lateinit var memory: UMemory<SingleTypeSystem.SingleType, Any?>
    lateinit var state: StateStub
    lateinit var translator: UExprTranslator<SingleTypeSystem.SingleType>
    lateinit var uSolver: USolverBase<SingleTypeSystem.SingleType, UContext>

    @BeforeEach
    fun initializeContext() {
        val components: UComponents<Type> = mockk()
        every { components.mkTypeSystem(any()) } returns mockk()

        ctx = UContext(components)
        pathConstraints = UPathConstraints(ctx)
        memory = UMemory(ctx, pathConstraints.typeConstraints)
        state = StateStub(ctx, pathConstraints, memory)

        val softConstraintProvider = USoftConstraintsProvider<SingleTypeSystem.SingleType>(ctx)
        val (translator, decoder) = buildTranslatorAndLazyDecoder<SingleTypeSystem.SingleType>(ctx)
        this.translator = translator
        val typeSolver = UTypeSolver(SingleTypeSystem)
        uSolver = USolverBase(ctx, KZ3Solver(ctx), typeSolver, translator, decoder, softConstraintProvider)
    }

    class StateStub(
        ctx: UContext,
        pathConstraints: UPathConstraints<SingleTypeSystem.SingleType, UContext>,
        memory: UMemory<SingleTypeSystem.SingleType, Any?>
    ) : UState<SingleTypeSystem.SingleType, Any?, Any?, UContext, StateStub>(
        ctx, UCallStack(),
        pathConstraints, memory, emptyList(), ctx.mkInitialLocation()
    ) {
        override fun clone(newConstraints: UPathConstraints<SingleTypeSystem.SingleType, UContext>?): StateStub {
            error("Unsupported")
        }

        override val isExceptional: Boolean
            get() = false
    }

    fun checkNoConcreteHeapRefs(expr: UExpr<*>) {
        // Translator throws exception if concrete ref occurs
        translator.translate(expr)
    }

    inline fun checkWithSolver(body: KSolver<*>.() -> Unit) {
        KZ3Solver(ctx).use { solver ->
            solver.body()
        }
    }

    fun KSolver<*>.assertPossible(mkCheck: UContext.() -> UBoolExpr) =
        assertStatus(KSolverStatus.SAT) { mkCheck() }

    fun KSolver<*>.assertImpossible(mkCheck: UContext.() -> UBoolExpr) =
        assertStatus(KSolverStatus.UNSAT) { mkCheck() }

    fun KSolver<*>.assertStatus(status: KSolverStatus, mkCheck: UContext.() -> UBoolExpr) = try {
        push()

        val expr = ctx.mkCheck()
        val solverExpr = translator.translate(expr)

        assert(solverExpr)

        val actualStatus = check()
        if (status != actualStatus) {
            println()
        }
        assertEquals(status, actualStatus)
    } finally {
        pop()
    }
}
