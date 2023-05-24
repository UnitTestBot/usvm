package org.usvm.interpreter

import org.usvm.UComponents
import org.usvm.UContext
import org.usvm.UTypeSystem
import org.usvm.language.Attribute
import org.usvm.language.Callable
import org.usvm.language.PythonType
import org.usvm.solver.USolverBase

object PythonComponents: UComponents<Attribute, PythonType, Callable> {
    override fun mkSolver(ctx: UContext): USolverBase<Attribute, PythonType, Callable> {
        TODO("Not yet implemented")
    }

    override fun mkTypeSystem(ctx: UContext): UTypeSystem<PythonType> {
        TODO("Not yet implemented")
    }
}