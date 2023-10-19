package org.usvm.machine

import io.ksmt.solver.yices.KYicesSolver
import io.ksmt.solver.z3.KZ3Solver
import org.usvm.SolverType
import org.usvm.UBv32SizeExprProvider
import org.usvm.UComponents
import org.usvm.UContext
import org.usvm.USizeExprProvider
import org.usvm.language.SampleType
import org.usvm.solver.USoftConstraintsProvider
import org.usvm.solver.USolverBase
import org.usvm.solver.UTypeSolver
import org.usvm.types.UTypeSystem

class SampleLanguageComponents(
    private val typeSystem: SampleTypeSystem,
    private val solverType: SolverType,
    useSolverForForks: Boolean
) : UComponents<SampleType, USizeSort>(useSolverForForks) {
    override fun <Context : UContext<USizeSort>> mkSolver(ctx: Context): USolverBase<SampleType> {
        val (translator, decoder) = buildTranslatorAndLazyDecoder(ctx)
        val softConstraintsProvider = USoftConstraintsProvider<SampleType, _>(ctx)

        val solver = when (solverType) {
            SolverType.YICES -> KYicesSolver(ctx)
            SolverType.Z3 -> KZ3Solver(ctx)
        }

        val typeSolver = UTypeSolver(typeSystem)
        return USolverBase(ctx, solver, typeSolver, translator, decoder, softConstraintsProvider)
    }

    override fun mkTypeSystem(ctx: UContext<USizeSort>): UTypeSystem<SampleType> = typeSystem

    override fun <Context : UContext<USizeSort>> mkSizeExprProvider(ctx: Context): USizeExprProvider<USizeSort> =
        UBv32SizeExprProvider(ctx)
}
