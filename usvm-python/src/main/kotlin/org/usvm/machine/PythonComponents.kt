package org.usvm.machine

import com.microsoft.z3.Global
import io.ksmt.solver.cvc5.KCvc5Solver
import io.ksmt.solver.yices.KYicesSolver
import io.ksmt.solver.z3.KZ3Solver
import org.usvm.UComponents
import org.usvm.UContext
import org.usvm.UTransformer
import org.usvm.language.PropertyOfPythonObject
import org.usvm.language.PythonCallable
import org.usvm.language.types.PythonType
import org.usvm.language.types.PythonTypeSystem
import org.usvm.model.buildTranslatorAndLazyDecoder
import org.usvm.solver.UExprTranslator
import org.usvm.solver.USoftConstraintsProvider
import org.usvm.solver.USolverBase
import org.usvm.solver.UTypeSolver
import org.usvm.types.UTypeSystem

class PythonComponents(
    private val typeSystem: PythonTypeSystem
): UComponents<PythonType> {
    override fun <Context : UContext> mkSolver(ctx: Context): USolverBase<PythonType, Context> {
        val (translator, decoder) = buildTranslatorAndLazyDecoder<PythonType>(ctx)
        val softConstraintsProvider = USoftConstraintsProvider<PythonType>(ctx)
        val solver = KZ3Solver(ctx)
//        solver.configure { setZ3Option("timeout", 1) }
        return USolverBase(ctx, solver, UTypeSolver(typeSystem),  translator, decoder, softConstraintsProvider)
    }

    override fun mkTypeSystem(ctx: UContext): UTypeSystem<PythonType> {
        return typeSystem
    }
}
