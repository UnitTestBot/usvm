package org.usvm.machine

import io.ksmt.solver.yices.KYicesSolver
import io.ksmt.solver.z3.KZ3Solver
import org.usvm.SolverType
import org.usvm.UComponents
import org.usvm.UContext
import org.usvm.language.Field
import org.usvm.language.Method
import org.usvm.language.SampleType
import org.usvm.model.buildTranslatorAndLazyDecoder
import org.usvm.solver.USoftConstraintsProvider
import org.usvm.solver.USolverBase
import org.usvm.solver.UTypeSolver
import org.usvm.types.UTypeSystem

class SampleLanguageComponents(
    private val typeSystem: SampleTypeSystem,
    private val solverType: SolverType
) : UComponents<Field<*>, SampleType, Method<*>> {
    override fun mkSolver(ctx: UContext): USolverBase<Field<*>, SampleType, Method<*>> {
        val (translator, decoder) = buildTranslatorAndLazyDecoder<Field<*>, SampleType, Method<*>>(ctx)
        val softConstraintsProvider = USoftConstraintsProvider<Field<*>, SampleType>(ctx)

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
