package org.usvm

import org.usvm.solver.USolverBase
import org.usvm.types.UTypeSystem

/**
 * Provides core USVM components tuned for specific language.
 * Instatiated once per [UContext].
 */
interface UComponents<Field, Type, Method> {
    fun <Context : UContext> mkSolver(ctx: Context): USolverBase<Field, Type, Method, Context>
    fun mkTypeSystem(ctx: UContext): UTypeSystem<Type>
}