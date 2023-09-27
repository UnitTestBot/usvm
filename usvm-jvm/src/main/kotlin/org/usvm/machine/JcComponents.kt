package org.usvm.machine

import io.ksmt.solver.yices.KYicesSolver
import io.ksmt.solver.z3.KZ3Solver
import io.ksmt.symfpu.solver.KSymFpuSolver
import org.jacodb.api.JcType
import org.usvm.SolverType
import org.usvm.UComponents
import org.usvm.UContext
import org.usvm.solver.USoftConstraintsProvider
import org.usvm.solver.USolverBase
import org.usvm.solver.UTypeSolver

class JcComponents(
    private val typeSystem: JcTypeSystem,
    private val solverType: SolverType,
) : UComponents<JcType> {
    private val closeableResources = mutableListOf<AutoCloseable>()

    override fun mkSolver(ctx: UContext): USolverBase<JcType> {
        val (translator, decoder) = buildTranslatorAndLazyDecoder(ctx)
        val softConstraintsProvider = USoftConstraintsProvider<JcType>(ctx)

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

    override fun mkTypeSystem(ctx: UContext): JcTypeSystem {
        return typeSystem
    }
}
