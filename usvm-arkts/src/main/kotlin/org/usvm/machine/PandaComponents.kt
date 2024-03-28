package org.usvm.machine

import io.ksmt.expr.KBitVec64Value
import io.ksmt.solver.yices.KYicesSolver
import io.ksmt.solver.z3.KZ3Solver
import io.ksmt.symfpu.solver.KSymFpuSolver
import org.jacodb.panda.dynamic.api.PandaType
import org.usvm.SolverType
import org.usvm.UBoolExpr
import org.usvm.UComponents
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UMachineOptions
import org.usvm.USizeExprProvider
import org.usvm.solver.USolverBase
import org.usvm.solver.UTypeSolver

/*
    Numbers in JavaScript are signed 64 bit doubles.
 */
class PandaComponents(
    private val typeSystem: PandaTypeSystem,
    private val options: UMachineOptions,
) : UComponents<PandaType, PandaNumberSort> {
    private val closeableResources = mutableListOf<AutoCloseable>()
    override val useSolverForForks: Boolean get() = options.useSolverForForks


    override fun <Context : UContext<PandaNumberSort>> mkSolver(ctx: Context): USolverBase<PandaType> {
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

    override fun mkTypeSystem(ctx: UContext<PandaNumberSort>): PandaTypeSystem {
        return typeSystem
    }

    override fun <Context : UContext<PandaNumberSort>> mkSizeExprProvider(ctx: Context): USizeExprProvider<PandaNumberSort> {
        return PandaFpSortSizeExprProvider(ctx as PandaContext)
    }
}

class PandaFpSortSizeExprProvider(
    override val ctx: PandaContext,
) : USizeExprProvider<PandaNumberSort> {
    override val sizeSort: PandaNumberSort = ctx.fp64Sort

    override fun mkSizeExpr(size: Int): UExpr<PandaNumberSort> = ctx.mkFp(size.toDouble(), sizeSort)
    override fun getIntValue(expr: UExpr<PandaNumberSort>): Int? = (expr as? KBitVec64Value)?.numberValue?.toInt()

    override fun mkSizeSubExpr(lhs: UExpr<PandaNumberSort>, rhs: UExpr<PandaNumberSort>): UExpr<PandaNumberSort> =
        ctx.mkFpSubExpr(ctx.fpRoundingModeSortDefaultValue(), lhs, rhs)

    override fun mkSizeAddExpr(lhs: UExpr<PandaNumberSort>, rhs: UExpr<PandaNumberSort>): UExpr<PandaNumberSort> =
        ctx.mkFpAddExpr(ctx.fpRoundingModeSortDefaultValue(), lhs, rhs)

    override fun mkSizeGtExpr(lhs: UExpr<PandaNumberSort>, rhs: UExpr<PandaNumberSort>): UBoolExpr =
        ctx.mkFpGreaterExpr(lhs, rhs)

    override fun mkSizeGeExpr(lhs: UExpr<PandaNumberSort>, rhs: UExpr<PandaNumberSort>): UBoolExpr =
        ctx.mkFpGreaterOrEqualExpr(lhs, rhs)

    override fun mkSizeLtExpr(lhs: UExpr<PandaNumberSort>, rhs: UExpr<PandaNumberSort>): UBoolExpr =
        ctx.mkFpLessExpr(lhs, rhs)

    override fun mkSizeLeExpr(lhs: UExpr<PandaNumberSort>, rhs: UExpr<PandaNumberSort>): UBoolExpr =
        ctx.mkFpLessOrEqualExpr(lhs, rhs)
}
