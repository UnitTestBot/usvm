package org.usvm

import io.ksmt.expr.KBitVec32Value
import io.ksmt.solver.yices.KYicesSolver
import io.ksmt.solver.z3.KZ3Solver
import io.ksmt.symfpu.solver.KSymFpuSolver
import org.jacodb.ets.base.EtsType
import org.usvm.solver.USolverBase
import org.usvm.solver.UTypeSolver
import org.usvm.types.UTypeSystem

class TSComponents(
    private val typeSystem: TSTypeSystem,
    private val options: UMachineOptions,
) : UComponents<EtsType, TSSizeSort> {
    private val closeableResources = mutableListOf<AutoCloseable>()

    override val useSolverForForks: Boolean
        get() = options.useSolverForForks

    override fun <Context : UContext<TSSizeSort>> mkSizeExprProvider(ctx: Context): USizeExprProvider<TSSizeSort> {
        return TSFpSortSizeExprProvider(ctx as TSContext)
    }

    override fun mkTypeSystem(
        ctx: UContext<TSSizeSort>,
    ): UTypeSystem<EtsType> = typeSystem

    override fun <Context : UContext<TSSizeSort>> mkSolver(ctx: Context): USolverBase<EtsType> {
        val (translator, decoder) = buildTranslatorAndLazyDecoder(ctx)

        val smtSolver = when (options.solverType) {
            SolverType.YICES -> KSymFpuSolver(KYicesSolver(ctx), ctx)
            SolverType.Z3 -> KZ3Solver(ctx)
        }

        closeableResources += smtSolver

        val typeSolver = UTypeSolver(typeSystem)

        return USolverBase(ctx, smtSolver, typeSolver, translator, decoder, options.solverTimeout)
    }

    fun close() {
        closeableResources.forEach(AutoCloseable::close)
    }
}

class TSFpSortSizeExprProvider(
    override val ctx: TSContext,
) : USizeExprProvider<TSSizeSort> {
    override val sizeSort: TSSizeSort = ctx.sizeSort

    override fun mkSizeExpr(size: Int): UExpr<TSSizeSort> = ctx.mkBv(size, sizeSort)
    override fun getIntValue(expr: UExpr<TSSizeSort>): Int? = (expr as? KBitVec32Value)?.numberValue

    override fun mkSizeSubExpr(lhs: UExpr<TSSizeSort>, rhs: UExpr<TSSizeSort>): UExpr<TSSizeSort> =
        ctx.mkBvSubExpr(lhs, rhs)

    override fun mkSizeAddExpr(lhs: UExpr<TSSizeSort>, rhs: UExpr<TSSizeSort>): UExpr<TSSizeSort> =
        ctx.mkBvAddExpr(lhs, rhs)

    override fun mkSizeGtExpr(lhs: UExpr<TSSizeSort>, rhs: UExpr<TSSizeSort>): UBoolExpr =
        ctx.mkBvUnsignedGreaterExpr(lhs, rhs)

    override fun mkSizeGeExpr(lhs: UExpr<TSSizeSort>, rhs: UExpr<TSSizeSort>): UBoolExpr =
        ctx.mkBvUnsignedGreaterOrEqualExpr(lhs, rhs)

    override fun mkSizeLtExpr(lhs: UExpr<TSSizeSort>, rhs: UExpr<TSSizeSort>): UBoolExpr =
        ctx.mkBvUnsignedLessExpr(lhs, rhs)

    override fun mkSizeLeExpr(lhs: UExpr<TSSizeSort>, rhs: UExpr<TSSizeSort>): UBoolExpr =
        ctx.mkBvUnsignedLessOrEqualExpr(lhs, rhs)
}
