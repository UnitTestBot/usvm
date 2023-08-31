package org.usvm

import org.usvm.solver.USolverBase
import org.usvm.types.UTypeSystem

/**
 * Provides core USVM components tuned for specific language.
 * Instatiated once per [UContext].
 */
interface UComponents<Type> {
    fun <Context : UContext> mkSolver(ctx: Context): USolverBase<Type, Context>
    fun mkTypeSystem(ctx: UContext): UTypeSystem<Type>
}