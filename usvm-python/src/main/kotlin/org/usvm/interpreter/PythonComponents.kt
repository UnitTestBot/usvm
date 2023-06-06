package org.usvm.interpreter

import io.ksmt.solver.bitwuzla.KBitwuzlaSolver
import io.ksmt.solver.yices.KYicesSolver
import io.ksmt.solver.z3.KZ3Solver
import org.usvm.UComponents
import org.usvm.UContext
import org.usvm.UTypeSystem
import org.usvm.language.Attribute
import org.usvm.language.Callable
import org.usvm.language.PythonType
import org.usvm.language.PythonTypeSystem
import org.usvm.model.buildTranslatorAndLazyDecoder
import org.usvm.solver.USoftConstraintsProvider
import org.usvm.solver.USolverBase

object PythonComponents: UComponents<Attribute, PythonType, Callable> {
    override fun mkSolver(ctx: UContext): USolverBase<Attribute, PythonType, Callable> {
        val (translator, decoder) = buildTranslatorAndLazyDecoder<Attribute, PythonType, Callable>(ctx)
        val softConstraintsProvider = USoftConstraintsProvider<Attribute, PythonType>(ctx)
        val solver = KYicesSolver(ctx)
        //solver.configure { setZ3Option("timeout", 100000) }
        return USolverBase(ctx, solver, translator, decoder, softConstraintsProvider)
    }
    
    override fun mkTypeSystem(ctx: UContext): UTypeSystem<PythonType> {
        return PythonTypeSystem
    }
}