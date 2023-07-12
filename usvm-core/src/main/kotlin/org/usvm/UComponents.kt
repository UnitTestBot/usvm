package org.usvm

import org.usvm.solver.USolverBase
import org.usvm.types.UTypeSystem

/**
 * Provides core USVM components tuned for specific language.
 * Instatiated once per [UContext].
 */
interface UComponents<Field, Type, Method> {
    fun mkSolver(ctx: UContext): USolverBase<Field, Type, Method>
    fun mkTypeSystem(ctx: UContext): UTypeSystem<Type>
}