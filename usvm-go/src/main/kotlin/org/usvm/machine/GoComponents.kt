package org.usvm.machine

import io.ksmt.solver.yices.KYicesSolver
import io.ksmt.solver.z3.KZ3Solver
import org.usvm.*
import org.usvm.solver.USolverBase
import org.usvm.solver.UTypeSolver
import org.usvm.types.UTypeSystem

class GoComponents(
    private val typeSystem: GoTypeSystem,
    private val options: UMachineOptions,
): UComponents<GoType, USizeSort> {
    override val useSolverForForks: Boolean get() = options.useSolverForForks

    override fun mkTypeSystem(ctx: UContext<USizeSort>): UTypeSystem<GoType> {
        return typeSystem
    }

    override fun <Context : UContext<USizeSort>> mkSolver(ctx: Context): USolverBase<GoType> {
        val (translator, decoder) = buildTranslatorAndLazyDecoder(ctx)
        val solver = when (options.solverType) {
            SolverType.YICES -> KYicesSolver(ctx)
            SolverType.Z3 -> KZ3Solver(ctx)
        }
        val typeSolver = UTypeSolver(typeSystem)

        return USolverBase(ctx, solver, typeSolver, translator, decoder, options.solverTimeout)
    }

    override fun <Context : UContext<USizeSort>> mkSizeExprProvider(ctx: Context): USizeExprProvider<USizeSort> {
        return UBv32SizeExprProvider(ctx)
    }
}