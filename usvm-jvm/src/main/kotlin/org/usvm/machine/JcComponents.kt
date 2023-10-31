package org.usvm.machine

import org.jacodb.api.JcType
import org.usvm.SolverType
import org.usvm.UBv32SizeExprProvider
import org.usvm.UComponents
import org.usvm.UContext
import org.usvm.USizeExprProvider
import org.usvm.solver.USolverBase
import org.usvm.solver.UTypeSolver

class JcComponents(
    private val typeSystem: JcTypeSystem,
    private val solverType: SolverType,
    override val useSolverForForks: Boolean,
    private val runSolverInAnotherProcess: Boolean,
) : UComponents<JcType, USizeSort> {
    private val closeableResources = mutableListOf<AutoCloseable>()

    override fun <Context : UContext<USizeSort>> mkSolver(ctx: Context): USolverBase<JcType> {
        val (translator, decoder) = buildTranslatorAndLazyDecoder(ctx)

        val solverFactory = SolverFactory.mkFactory(runSolverInAnotherProcess)
        val smtSolver = solverFactory.mkSolver(ctx, solverType)
        val typeSolver = UTypeSolver(typeSystem)
        closeableResources += smtSolver
        closeableResources += solverFactory

        return USolverBase(ctx, smtSolver, typeSolver, translator, decoder)
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
