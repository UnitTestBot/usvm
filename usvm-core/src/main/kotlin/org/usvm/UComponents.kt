package org.usvm

import org.usvm.solver.USolverBase
import org.usvm.types.UTypeSystem

/**
 * Provides core USVM components tuned for specific language.
 * Instantiated once per [UContext].
 */
interface UComponents<Type, USizeSort : USort> {
    fun <Context : UContext<USizeSort>> mkSolver(ctx: Context): USolverBase<Type, Context>
    fun mkTypeSystem(ctx: UContext<USizeSort>): UTypeSystem<Type>
    fun <Context : UContext<USizeSort>> mkSizeExprProvider(ctx: Context): USizeExprProvider<USizeSort>
}