package org.usvm.interpreter

import io.ksmt.solver.z3.KZ3Solver
import org.usvm.UComponents
import org.usvm.UContext
import org.usvm.UTypeSystem
import org.usvm.language.Attribute
import org.usvm.language.PythonCallable
import org.usvm.language.PythonType
import org.usvm.language.PythonTypeSystem
import org.usvm.model.buildTranslatorAndLazyDecoder
import org.usvm.solver.USoftConstraintsProvider
import org.usvm.solver.USolverBase

object PythonComponents: UComponents<Attribute, PythonType, PythonCallable> {
    override fun mkSolver(ctx: UContext): USolverBase<Attribute, PythonType, PythonCallable> {
        val (translator, decoder) = buildTranslatorAndLazyDecoder<Attribute, PythonType, PythonCallable>(ctx)
        val softConstraintsProvider = USoftConstraintsProvider<Attribute, PythonType>(ctx)
        val solver = KZ3Solver(ctx)
        //solver.configure { setZ3Option("timeout", 100000) }
        return USolverBase(ctx, solver, translator, decoder, softConstraintsProvider)
    }

    override fun mkTypeSystem(ctx: UContext): UTypeSystem<PythonType> {
        return PythonTypeSystem
    }
}