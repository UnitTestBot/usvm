package org.usvm.machine

import io.ksmt.solver.yices.KYicesSolver
import io.ksmt.solver.z3.KZ3Solver
import org.usvm.SolverType
import org.usvm.UComponents
import org.usvm.UContext
import org.usvm.language.SampleType
import org.usvm.model.buildTranslatorAndLazyDecoder
import org.usvm.solver.USoftConstraintsProvider
import org.usvm.solver.USolverBase
import org.usvm.solver.UTypeSolver
import org.usvm.types.UTypeSystem
import kotlin.time.Duration

class SampleLanguageComponents(
    private val typeSystem: SampleTypeSystem,
    private val solverType: SolverType,
    override val solverTimeout: Duration
) : UComponents<SampleType> {
    override fun <Context : UContext> mkSolver(ctx: Context): USolverBase<SampleType, Context> {
        val (translator, decoder) = buildTranslatorAndLazyDecoder<SampleType>(ctx)
        val softConstraintsProvider = USoftConstraintsProvider<SampleType>(ctx)

        val solver =
            when (solverType) {
                SolverType.YICES -> KYicesSolver(ctx)
                SolverType.Z3 -> KZ3Solver(ctx)
            }

        val typeSolver = UTypeSolver(typeSystem)
        return USolverBase(ctx, solver, typeSolver, translator, decoder, softConstraintsProvider)
    }

    override fun mkTypeSystem(ctx: UContext): UTypeSystem<SampleType> = typeSystem
}
