package org.usvm.machine

import io.ksmt.expr.KBitVec64Value
import io.ksmt.solver.yices.KYicesSolver
import io.ksmt.solver.z3.KZ3Solver
import org.jacodb.panda.dynamic.api.PandaType
import org.usvm.*
import org.usvm.solver.USolverBase
import org.usvm.solver.UTypeSolver

/*
    Numbers in JavaScript are signed 64 bit doubles.
 */
class PandaComponents(
    private val typeSystem: PandaTypeSystem,
    private val options: UMachineOptions
) : UComponents<PandaType, PandaFp64Sort> {
    private val closeableResources = mutableListOf<AutoCloseable>()
    override val useSolverForForks: Boolean get() = options.useSolverForForks

    override fun <Context : UContext<PandaFp64Sort>> mkSolver(ctx: Context): USolverBase<PandaType> {
        val (translator, decoder) = buildTranslatorAndLazyDecoder(ctx)
        val solver = when (options.solverType) {
            SolverType.YICES -> KYicesSolver(ctx)
            SolverType.Z3 -> KZ3Solver(ctx)
        }
        val typeSolver = UTypeSolver(typeSystem)

        return USolverBase(ctx, solver, typeSolver, translator, decoder, options.solverTimeout)
    }

    fun close() {
        closeableResources.forEach(AutoCloseable::close)
    }

    override fun mkTypeSystem(ctx: UContext<PandaFp64Sort>): PandaTypeSystem {
        return typeSystem
    }

    override fun <Context : UContext<PandaFp64Sort>> mkSizeExprProvider(ctx: Context): USizeExprProvider<PandaFp64Sort> {
        return PandaFpSortSizeExprProvider(ctx as PandaContext)
    }
}

class PandaFpSortSizeExprProvider(
    override val ctx: PandaContext
) : USizeExprProvider<PandaFp64Sort> {
    override val sizeSort: PandaFp64Sort = ctx.fp64Sort

    override fun mkSizeExpr(size: Int): UExpr<PandaFp64Sort> = ctx.mkFp(size.toDouble(), sizeSort)
    override fun getIntValue(expr: UExpr<PandaFp64Sort>): Int? = (expr as? KBitVec64Value)?.numberValue?.toInt()

    override fun mkSizeSubExpr(lhs: UExpr<PandaFp64Sort>, rhs: UExpr<PandaFp64Sort>): UExpr<PandaFp64Sort> =
        ctx.mkFpSubExpr(ctx.fpRoundingModeSortDefaultValue(), lhs, rhs)
    override fun mkSizeAddExpr(lhs: UExpr<PandaFp64Sort>, rhs: UExpr<PandaFp64Sort>): UExpr<PandaFp64Sort> =
        ctx.mkFpAddExpr(ctx.fpRoundingModeSortDefaultValue(), lhs, rhs)
    override fun mkSizeGtExpr(lhs: UExpr<PandaFp64Sort>, rhs: UExpr<PandaFp64Sort>): UBoolExpr =
        ctx.mkFpGreaterExpr(lhs, rhs)
    override fun mkSizeGeExpr(lhs: UExpr<PandaFp64Sort>, rhs: UExpr<PandaFp64Sort>): UBoolExpr =
        ctx.mkFpGreaterOrEqualExpr(lhs, rhs)
    override fun mkSizeLtExpr(lhs: UExpr<PandaFp64Sort>, rhs: UExpr<PandaFp64Sort>): UBoolExpr =
        ctx.mkFpLessExpr(lhs, rhs)
    override fun mkSizeLeExpr(lhs: UExpr<PandaFp64Sort>, rhs: UExpr<PandaFp64Sort>): UBoolExpr =
        ctx.mkFpLessOrEqualExpr(lhs, rhs)
}
