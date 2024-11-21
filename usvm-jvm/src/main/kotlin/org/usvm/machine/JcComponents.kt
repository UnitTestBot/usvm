package org.usvm.machine

import org.jacodb.api.jvm.JcType
import org.usvm.UBv32SizeExprProvider
import org.usvm.UComponents
import org.usvm.UComposer
import org.usvm.UContext
import org.usvm.UMachineOptions
import org.usvm.USizeExprProvider
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.memory.UReadOnlyMemory
import org.usvm.model.ULazyModelDecoder
import org.usvm.solver.UExprTranslator
import org.usvm.solver.USoftConstraintsProvider
import org.usvm.solver.USolverBase
import org.usvm.solver.UTypeSolver

class JcComponents(
    private val typeSystem: JcTypeSystem,
    // TODO specific JcMachineOptions should be here
    private val options: UMachineOptions,
) : UComponents<JcType, USizeSort> {
    private val closeableResources = mutableListOf<AutoCloseable>()
    override val useSolverForForks: Boolean get() = options.useSolverForForks

    override fun <Context : UContext<USizeSort>> buildTranslatorAndLazyDecoder(
        ctx: Context,
    ): Pair<UExprTranslator<JcType, USizeSort>, ULazyModelDecoder<JcType>> {
        val translator = JcExprTranslator(ctx)
        val decoder: ULazyModelDecoder<JcType> = ULazyModelDecoder(translator)

        return translator to decoder
    }

    override fun <Context : UContext<USizeSort>> mkComposer(
        ctx: Context
    ): (UReadOnlyMemory<JcType>, MutabilityOwnership) -> UComposer<JcType, USizeSort> =
        { memory: UReadOnlyMemory<JcType>, ownership: MutabilityOwnership -> JcComposer(ctx, memory, ownership) }

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

    override fun <Context : UContext<USizeSort>> mkSoftConstraintsProvider(
        ctx: Context,
    ): USoftConstraintsProvider<JcType, USizeSort> = JcSoftConstraintsProvider(ctx)
}
