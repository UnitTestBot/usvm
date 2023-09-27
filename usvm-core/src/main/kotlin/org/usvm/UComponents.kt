package org.usvm

import org.usvm.model.ULazyModelDecoder
import org.usvm.model.UModelDecoder
import org.usvm.solver.UExprTranslator
import org.usvm.solver.USolverBase
import org.usvm.types.UTypeSystem

/**
 * Provides core USVM components tuned for specific language.
 * Instantiated once per [UContext].
 */
interface UComponents<Type> {
    fun mkSolver(ctx: UContext): USolverBase<Type>
    fun mkTypeSystem(ctx: UContext): UTypeSystem<Type>

    /**
     * Initializes [UExprTranslator] and [UModelDecoder] and returns them. We can safely reuse them while [UContext] is
     * alive.
     */
    fun buildTranslatorAndLazyDecoder(
        ctx: UContext,
    ): Pair<UExprTranslator<Type>, ULazyModelDecoder<Type>> {
        val translator = UExprTranslator<Type>(ctx)
        val decoder = ULazyModelDecoder(translator)

        return translator to decoder
    }
}