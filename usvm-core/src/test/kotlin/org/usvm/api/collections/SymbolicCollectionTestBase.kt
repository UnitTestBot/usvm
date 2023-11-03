package org.usvm.api.collections

import io.ksmt.solver.KSolver
import io.ksmt.solver.KSolverStatus
import io.ksmt.solver.z3.KZ3Solver
import org.junit.jupiter.api.BeforeEach
import org.usvm.PathNode
import org.usvm.StepScope
import org.usvm.UBoolExpr
import org.usvm.UBv32SizeExprProvider
import org.usvm.UCallStack
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.USizeSort
import org.usvm.UState
import org.usvm.WithSolverStateForker
import org.usvm.constraints.UPathConstraints
import org.usvm.forkblacklists.UForkBlackList
import org.usvm.memory.UMemory
import org.usvm.model.ULazyModelDecoder
import org.usvm.solver.UExprTranslator
import org.usvm.solver.USolverBase
import org.usvm.solver.UTypeSolver
import org.usvm.targets.UTarget
import org.usvm.targets.UTargetsSet
import org.usvm.types.single.SingleTypeSystem
import kotlin.test.assertEquals

abstract class SymbolicCollectionTestBase {
    private val typeSystem = SingleTypeSystem

    lateinit var ctx: UContext<USizeSort>
    lateinit var pathConstraints: UPathConstraints<SingleTypeSystem.SingleType>
    lateinit var memory: UMemory<SingleTypeSystem.SingleType, Any?>
    lateinit var scope: StepScope<StateStub, SingleTypeSystem.SingleType, *, UContext<USizeSort>>
    lateinit var translator: UExprTranslator<SingleTypeSystem.SingleType, USizeSort>
    lateinit var uSolver: USolverBase<SingleTypeSystem.SingleType>

    @BeforeEach
    fun initializeContext() {
        ctx = UContext(UBv32SizeExprProvider)

        val translator = UExprTranslator<SingleTypeSystem.SingleType, USizeSort>(ctx)
        val decoder = ULazyModelDecoder(typeSystem, translator)
        this.translator = translator
        val typeSolver = UTypeSolver(typeSystem)
        uSolver = USolverBase(ctx, KZ3Solver(ctx), typeSolver, translator, decoder)


        pathConstraints = UPathConstraints.empty(ctx, typeSystem)
        memory = UMemory(ctx, pathConstraints.typeConstraints)
        scope = StepScope(
            StateStub(ctx, pathConstraints, memory),
            UForkBlackList.createDefault(),
            WithSolverStateForker(uSolver)
        )
    }

    class TargetStub : UTarget<Any?, TargetStub>()

    class StateStub(
        ctx: UContext<USizeSort>,
        pathConstraints: UPathConstraints<SingleTypeSystem.SingleType>,
        memory: UMemory<SingleTypeSystem.SingleType, Any?>,
    ) : UState<SingleTypeSystem.SingleType, Any?, Any?, UContext<USizeSort>, TargetStub, StateStub>(
        ctx, UCallStack(),
        pathConstraints, memory, emptyList(), PathNode.root(), UTargetsSet.empty()
    ) {
        override fun clone(newConstraints: UPathConstraints<SingleTypeSystem.SingleType>?): StateStub {
            val clonedConstraints = newConstraints ?: pathConstraints.clone()
            return StateStub(ctx, clonedConstraints, memory.clone(clonedConstraints.typeConstraints))
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

    fun KSolver<*>.assertPossible(mkCheck: UContext<*>.() -> UBoolExpr) =
        assertStatus(KSolverStatus.SAT) { mkCheck() }

    fun KSolver<*>.assertImpossible(mkCheck: UContext<*>.() -> UBoolExpr) =
        assertStatus(KSolverStatus.UNSAT) { mkCheck() }

    fun KSolver<*>.assertStatus(status: KSolverStatus, mkCheck: UContext<*>.() -> UBoolExpr) = try {
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
