package org.usvm

import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.memory.UReadOnlyMemory
import org.usvm.model.ULazyModelDecoder
import org.usvm.model.UModelDecoder
import org.usvm.solver.UExprTranslator
import org.usvm.solver.USoftConstraintsProvider
import org.usvm.solver.USolverBase
import org.usvm.types.UTypeSystem

/**
 * Provides core USVM components tuned for specific language.
 * Instantiated once per [UContext].
 */
interface UComponents<Type, USizeSort : USort> {
    val useSolverForForks: Boolean

    fun <Context : UContext<USizeSort>> mkSolver(ctx: Context): USolverBase<Type>
    fun mkTypeSystem(ctx: UContext<USizeSort>): UTypeSystem<Type>
    fun <Context : UContext<USizeSort>> mkSizeExprProvider(ctx: Context): USizeExprProvider<USizeSort>

    /**
     * Initializes [UExprTranslator] and [UModelDecoder] and returns them. We can safely reuse them while [UContext] is
     * alive.
     */
    fun <Context : UContext<USizeSort>> buildTranslatorAndLazyDecoder(
        ctx: Context,
    ): Pair<UExprTranslator<Type, USizeSort>, ULazyModelDecoder<Type>> {
        val translator = UExprTranslator<Type, USizeSort>(ctx)
        val decoder = ULazyModelDecoder(translator)

        return translator to decoder
    }

    fun <Context : UContext<USizeSort>> mkComposer(
        ctx: Context,
    ): (UReadOnlyMemory<Type>, MutabilityOwnership) -> UComposer<Type, USizeSort> =
        { memory: UReadOnlyMemory<Type>, ownership: MutabilityOwnership -> UComposer(ctx, memory, ownership) }

    fun mkStatesForkProvider(): StateForker = if (useSolverForForks) WithSolverStateForker else NoSolverStateForker

    fun <Context : UContext<USizeSort>> mkSoftConstraintsProvider(
        ctx: Context
    ): USoftConstraintsProvider<Type, USizeSort> = USoftConstraintsProvider(ctx)
}
