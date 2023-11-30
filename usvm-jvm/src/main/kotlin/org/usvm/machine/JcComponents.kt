package org.usvm.machine

import io.ksmt.utils.cast
import org.jacodb.api.JcType
import org.usvm.UBv32SizeExprProvider
import org.usvm.UComponents
import org.usvm.UContext
import org.usvm.UMachineOptions
import org.usvm.UMocker
import org.usvm.USizeExprProvider
import org.usvm.solver.USolverBase
import org.usvm.solver.UTypeSolver
import org.usvm.types.JcTypeSystem

class JcComponents(
    private val typeSystem: JcTypeSystem,
    // TODO specific JcMachineOptions should be here
    private val options: UMachineOptions,
) : UComponents<JcType, USizeSort> {
    private val closeableResources = mutableListOf<AutoCloseable>()
    override val useSolverForForks: Boolean get() = options.useSolverForForks

    override fun <Context : UContext<USizeSort>> mkSolver(ctx: Context): USolverBase<JcType> {
        val (translator, decoder) = buildTranslatorAndLazyDecoder(ctx)

        val solverFactory = SolverFactory.mkFactory(options.runSolverInAnotherProcess)
        val smtSolver = solverFactory.mkSolver(ctx, options.solverType)
        val typeSolver = UTypeSolver(typeSystem)
        closeableResources += smtSolver
        closeableResources += solverFactory

        return USolverBase(ctx, smtSolver, typeSolver, translator, decoder, options.solverTimeout)
    }

    fun close() {
        closeableResources.forEach(AutoCloseable::close)
    }

    override fun mkTypeSystem(ctx: UContext<USizeSort>): JcTypeSystem {
        return typeSystem
    }

    override fun <Context : UContext<USizeSort>> mkSizeExprProvider(ctx: Context): USizeExprProvider<USizeSort> =
        UBv32SizeExprProvider(ctx)

    override fun <Method> mkMocker(): UMocker<Method> = JcMocker().cast()
}
