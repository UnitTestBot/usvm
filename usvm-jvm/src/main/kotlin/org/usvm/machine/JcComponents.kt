package org.usvm.machine

import io.ksmt.solver.yices.KYicesSolver
import io.ksmt.solver.z3.KZ3Solver
import io.ksmt.symfpu.solver.KSymFpuSolver
import org.jacodb.api.JcType
import org.usvm.SolverType
import org.usvm.UBv32SizeExprProvider
import org.usvm.UComponents
import org.usvm.UContext
import org.usvm.USizeExprProvider
import org.usvm.solver.USoftConstraintsProvider
import org.usvm.solver.USolverBase
import org.usvm.solver.UTypeSolver

class JcComponents(
    private val typeSystem: JcTypeSystem,
    private val solverType: SolverType,
    useSolverForForks: Boolean,
) : UComponents<JcType, USizeSort>(useSolverForForks) {
    private val closeableResources = mutableListOf<AutoCloseable>()

    override fun <Context : UContext<USizeSort>> mkSolver(ctx: Context): USolverBase<JcType> {
        val (translator, decoder) = buildTranslatorAndLazyDecoder(ctx)
        val softConstraintsProvider = USoftConstraintsProvider<JcType, _>(ctx)

        val smtSolver = when (solverType) {
            // Yices with Fp support via SymFpu
            SolverType.YICES -> KSymFpuSolver(KYicesSolver(ctx), ctx)
            SolverType.Z3 -> KZ3Solver(ctx)
        }
        val typeSolver = UTypeSolver(typeSystem)
        closeableResources += smtSolver

        return USolverBase(ctx, smtSolver, typeSolver, translator, decoder, softConstraintsProvider)
    }

    fun close() {
        closeableResources.forEach(AutoCloseable::close)
    }

    override fun mkTypeSystem(ctx: UContext<USizeSort>): JcTypeSystem {
        return typeSystem
    }

    override fun <Context : UContext<USizeSort>> mkSizeExprProvider(ctx: Context): USizeExprProvider<USizeSort> =
        UBv32SizeExprProvider(ctx)
}
