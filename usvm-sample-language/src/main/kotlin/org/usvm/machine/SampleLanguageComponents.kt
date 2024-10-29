package org.usvm.machine

import io.ksmt.solver.yices.KYicesSolver
import io.ksmt.solver.z3.KZ3Solver
import org.usvm.SolverType
import org.usvm.UBv32SizeExprProvider
import org.usvm.UComponents
import org.usvm.UContext
import org.usvm.UMachineOptions
import org.usvm.USizeExprProvider
import org.usvm.language.SampleType
import org.usvm.solver.UDumbStringSolver
import org.usvm.solver.USolverBase
import org.usvm.solver.UTypeSolver
import org.usvm.types.UTypeSystem

class SampleLanguageComponents(
    private val typeSystem: SampleTypeSystem,
    // TODO specific SampleMachineOptions should be here
    private val options: UMachineOptions,
) : UComponents<SampleType, USizeSort> {
    override val useSolverForForks: Boolean get() = options.useSolverForForks

    override fun <Context : UContext<USizeSort>> mkSolver(ctx: Context): USolverBase<SampleType> {
        val (translator, decoder) = buildTranslatorAndLazyDecoder(ctx)

        val solver = when (options.solverType) {
            SolverType.YICES -> KYicesSolver(ctx)
            SolverType.Z3 -> KZ3Solver(ctx)
        }

        val typeSolver = UTypeSolver(typeSystem)
        val stringSolver = UDumbStringSolver(ctx)
        return USolverBase(ctx, solver, typeSolver, stringSolver, translator, decoder, options.solverTimeout)
    }

    override fun mkTypeSystem(ctx: UContext<USizeSort>): UTypeSystem<SampleType> = typeSystem

    override fun <Context : UContext<USizeSort>> mkSizeExprProvider(ctx: Context): USizeExprProvider<USizeSort> =
        UBv32SizeExprProvider(ctx)
}
