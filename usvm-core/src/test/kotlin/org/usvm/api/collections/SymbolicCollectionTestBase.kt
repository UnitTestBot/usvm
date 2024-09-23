package org.usvm.api.collections

import io.ksmt.solver.KSolver
import io.ksmt.solver.KSolverStatus
import io.ksmt.solver.z3.KZ3Solver
import io.ksmt.utils.uncheckedCast
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.usvm.PathNode
import org.usvm.StepScope
import org.usvm.UBoolExpr
import org.usvm.UBv32SizeExprProvider
import org.usvm.UCallStack
import org.usvm.UComponents
import org.usvm.UComposer
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.USizeSort
import org.usvm.UState
import org.usvm.WithSolverStateForker
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.constraints.UPathConstraints
import org.usvm.forkblacklists.UForkBlackList
import org.usvm.memory.UMemory
import org.usvm.memory.UReadOnlyMemory
import org.usvm.model.ULazyModelDecoder
import org.usvm.solver.UExprTranslator
import org.usvm.solver.USolverBase
import org.usvm.solver.UTypeSolver
import org.usvm.targets.UTarget
import org.usvm.targets.UTargetsSet
import org.usvm.types.single.SingleTypeSystem
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.INFINITE

abstract class SymbolicCollectionTestBase {
    lateinit var ctx: UContext<USizeSort>
    lateinit var ownership: MutabilityOwnership
    lateinit var pathConstraints: UPathConstraints<SingleTypeSystem.SingleType>
    lateinit var memory: UMemory<SingleTypeSystem.SingleType, Any?>
    lateinit var scope: StepScope<StateStub, SingleTypeSystem.SingleType, *, UContext<USizeSort>>
    lateinit var translator: UExprTranslator<SingleTypeSystem.SingleType, USizeSort>
    lateinit var uSolver: USolverBase<SingleTypeSystem.SingleType>

    @BeforeEach
    fun initializeContext() {
        val components: UComponents<SingleTypeSystem.SingleType, USizeSort> = mockk()
        every { components.mkTypeSystem(any()) } returns mockk()
        every { components.mkSolver(any()) } answers { uSolver.uncheckedCast() }
        ctx = UContext(components)
        ownership = MutabilityOwnership()

        every { components.mkComposer(ctx) } answers {
            { memory: UReadOnlyMemory<SingleTypeSystem.SingleType>, ownership: MutabilityOwnership -> UComposer(ctx, memory, ownership) }
        }

        val translator = UExprTranslator<SingleTypeSystem.SingleType, USizeSort>(ctx)
        val decoder = ULazyModelDecoder(translator)
        this.translator = translator
        val typeSolver = UTypeSolver(SingleTypeSystem)
        uSolver = USolverBase(ctx, KZ3Solver(ctx), typeSolver, translator, decoder, timeout = INFINITE)
        every { components.mkSizeExprProvider(any()) } answers { UBv32SizeExprProvider(ctx) }
        every { components.mkStatesForkProvider() } answers { WithSolverStateForker }


        pathConstraints = UPathConstraints(ctx, ownership)
        memory = UMemory(ctx, ownership, pathConstraints.typeConstraints)
        scope = StepScope(StateStub(ctx, ownership, pathConstraints, memory), UForkBlackList.createDefault())
    }

    class TargetStub : UTarget<Any?, TargetStub>()

    class StateStub(
        ctx: UContext<USizeSort>,
        ownership: MutabilityOwnership,
        pathConstraints: UPathConstraints<SingleTypeSystem.SingleType>,
        memory: UMemory<SingleTypeSystem.SingleType, Any?>,
    ) : UState<SingleTypeSystem.SingleType, Any?, Any?, UContext<USizeSort>, TargetStub, StateStub>(
        ctx, ownership, UCallStack(),
        pathConstraints, memory, emptyList(), PathNode.root(), PathNode.root(), UTargetsSet.empty()
    ) {
        override fun clone(newConstraints: UPathConstraints<SingleTypeSystem.SingleType>?): StateStub {
            val thisOwnership = MutabilityOwnership()
            val cloneOwnership = MutabilityOwnership()
            val clonedConstraints = newConstraints ?: pathConstraints.clone(thisOwnership, cloneOwnership)
            return StateStub(
                ctx,
                cloneOwnership,
                clonedConstraints,
                memory.clone(clonedConstraints.typeConstraints, thisOwnership, cloneOwnership)
            )
        }

        override val isExceptional: Boolean
            get() = false

        override val entrypoint: Any? = null
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
