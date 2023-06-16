package org.usvm.intrinsics.collections

import io.ksmt.solver.KSolver
import io.ksmt.solver.KSolverStatus
import io.ksmt.solver.z3.KZ3Solver
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.BeforeEach
import org.usvm.*
import org.usvm.constraints.UPathConstraints
import org.usvm.memory.UMemoryBase
import org.usvm.solver.UExprTranslator
import kotlin.test.assertEquals

abstract class SymbolicCollectionTestBase {
    lateinit var ctx: UContext
    lateinit var pathConstraints: UPathConstraints<Type>
    lateinit var memory: UMemoryBase<Field, Type, Any?>
    lateinit var state: StateStub
    lateinit var translator: UExprTranslator<Field, Type>

    @BeforeEach
    fun initializeContext() {
        val components: UComponents<*, *, *> = mockk()
        every { components.mkTypeSystem(any()) } returns mockk()

        ctx = UContext(components)
        pathConstraints = UPathConstraints(ctx)
        memory = UMemoryBase(ctx, pathConstraints.typeConstraints)
        state = StateStub(ctx, pathConstraints, memory)

        translator = UExprTranslator(ctx)
    }

    class StateStub(
        ctx: UContext,
        pathConstraints: UPathConstraints<Type>,
        memory: UMemoryBase<Field, Type, Any?>
    ) : UState<Type, Field, Any?, Any?>(
        ctx, UCallStack(),
        pathConstraints, memory, emptyList(), persistentListOf()
    ) {
        override fun clone(newConstraints: UPathConstraints<Type>?): UState<Type, Field, Any?, Any?> {
            error("Unsupported")
        }
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
        assertEquals(status, actualStatus)
    } finally {
        pop()
    }
}
