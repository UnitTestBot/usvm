package org.usvm.machine

import io.ksmt.solver.yices.KYicesSolver
import io.ksmt.solver.z3.KZ3Solver
import io.ksmt.symfpu.solver.KSymFpuSolver
import org.jacodb.ets.model.EtsType
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

class TsComponents(
    private val typeSystem: TsTypeSystem,
    private val options: UMachineOptions,
) : UComponents<EtsType, TsSizeSort> {
    private val closeableResources = mutableListOf<AutoCloseable>()

    override val useSolverForForks: Boolean
        get() = options.useSolverForForks

    override fun <Context : UContext<TsSizeSort>> buildTranslatorAndLazyDecoder(
        ctx: Context,
    ): Pair<UExprTranslator<EtsType, TsSizeSort>, ULazyModelDecoder<EtsType>> {
        val translator = TsExprTranslator(ctx)
        val decoder = ULazyModelDecoder(translator)

        return translator to decoder
    }

    override fun <Context : UContext<TsSizeSort>> mkSizeExprProvider(ctx: Context): USizeExprProvider<TsSizeSort> {
        return UBv32SizeExprProvider(ctx)
    }

    override fun <Context : UContext<TsSizeSort>> mkComposer(
        ctx: Context,
    ): (UReadOnlyMemory<EtsType>, MutabilityOwnership) -> UComposer<EtsType, TsSizeSort> =
        { memory: UReadOnlyMemory<EtsType>, ownership: MutabilityOwnership ->
            TsComposer(ctx, memory, ownership)
        }

    override fun mkTypeSystem(
        ctx: UContext<TsSizeSort>,
    ): UTypeSystem<EtsType> = typeSystem

    override fun <Context : UContext<TsSizeSort>> mkSolver(ctx: Context): USolverBase<EtsType> {
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
