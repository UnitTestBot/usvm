package org.usvm

import org.usvm.solver.USolverBase

/**
 * Provides core USVM components tuned for specific language.
 * Instatiated once per [UContext].
 */
interface UComponents<Type, Method> {
    fun mkSolver(ctx: UContext): USolverBase<Type, Method>
    fun mkTypeSystem(ctx: UContext): UTypeSystem<Type>
}