package org.usvm.interpreter

import io.ksmt.solver.z3.KZ3Solver
import org.usvm.UComponents
import org.usvm.UContext
import org.usvm.UTypeSystem
import org.usvm.language.PropertyOfPythonObject
import org.usvm.language.PythonCallable
import org.usvm.language.PythonType
import org.usvm.language.PythonTypeSystem
import org.usvm.model.buildTranslatorAndLazyDecoder
import org.usvm.solver.USoftConstraintsProvider
import org.usvm.solver.USolverBase

object PythonComponents: UComponents<PropertyOfPythonObject, PythonType, PythonCallable> {
    override fun mkSolver(ctx: UContext): USolverBase<PropertyOfPythonObject, PythonType, PythonCallable> {
        val (translator, decoder) = buildTranslatorAndLazyDecoder<PropertyOfPythonObject, PythonType, PythonCallable>(ctx)
        val softConstraintsProvider = USoftConstraintsProvider<PropertyOfPythonObject, PythonType>(ctx)
        val solver = KZ3Solver(ctx)
        //solver.configure { setZ3Option("timeout", 100000) }
        return USolverBase(ctx, solver, translator, decoder, softConstraintsProvider)
    }

    override fun mkTypeSystem(ctx: UContext): UTypeSystem<PythonType> {
        return PythonTypeSystem
    }
}