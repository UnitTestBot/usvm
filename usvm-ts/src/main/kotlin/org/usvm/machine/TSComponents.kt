package org.usvm.machine

import io.ksmt.solver.yices.KYicesSolver
import io.ksmt.solver.z3.KZ3Solver
import io.ksmt.symfpu.solver.KSymFpuSolver
import org.jacodb.ets.base.EtsType
import org.usvm.SolverType
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
import org.usvm.solver.USolverBase
import org.usvm.solver.UTypeSolver
import org.usvm.types.UTypeSystem

class TSComponents(
    private val typeSystem: TSTypeSystem,
    private val options: UMachineOptions,
) : UComponents<EtsType, TSSizeSort> {
    private val closeableResources = mutableListOf<AutoCloseable>()

    override val useSolverForForks: Boolean
        get() = options.useSolverForForks

    override fun <Context : UContext<TSSizeSort>> buildTranslatorAndLazyDecoder(
        ctx: Context
    ): Pair<UExprTranslator<EtsType, TSSizeSort>, ULazyModelDecoder<EtsType>> {
        val translator = TSExprTranslator(ctx)
        val decoder = ULazyModelDecoder(translator)

        return translator to decoder
    }

    override fun <Context : UContext<TSSizeSort>> mkSizeExprProvider(ctx: Context): USizeExprProvider<TSSizeSort> {
        return UBv32SizeExprProvider(ctx)
    }

    override fun <Context : UContext<TSSizeSort>> mkComposer(
        ctx: Context
    ): (UReadOnlyMemory<EtsType>, MutabilityOwnership) -> UComposer<EtsType, TSSizeSort> =
        { memory: UReadOnlyMemory<EtsType>, ownership: MutabilityOwnership ->
            TSComposer(ctx, memory, ownership)
        }

    override fun mkTypeSystem(
        ctx: UContext<TSSizeSort>,
    ): UTypeSystem<EtsType> = typeSystem

    override fun <Context : UContext<TSSizeSort>> mkSolver(ctx: Context): USolverBase<EtsType> {
        val (translator, decoder) = buildTranslatorAndLazyDecoder(ctx)

        val smtSolver = when (options.solverType) {
            SolverType.YICES -> KSymFpuSolver(KYicesSolver(ctx), ctx)
            SolverType.Z3 -> KZ3Solver(ctx)
        }

        closeableResources += smtSolver

        val typeSolver = UTypeSolver(typeSystem)

        return USolverBase(ctx, smtSolver, typeSolver, translator, decoder, options.solverTimeout)
    }

    fun close() {
        closeableResources.forEach(AutoCloseable::close)
    }
}
