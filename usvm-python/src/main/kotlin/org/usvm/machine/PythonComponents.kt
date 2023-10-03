package org.usvm.machine

import io.ksmt.solver.z3.KZ3Solver
import io.ksmt.sort.KIntSort
import org.usvm.*
import org.usvm.language.types.PythonType
import org.usvm.language.types.PythonTypeSystem
import org.usvm.solver.USoftConstraintsProvider
import org.usvm.solver.USolverBase
import org.usvm.solver.UTypeSolver
import org.usvm.types.UTypeSystem

class PythonComponents(
    private val typeSystem: PythonTypeSystem
): UComponents<PythonType, KIntSort> {
    override fun <Context : UContext<KIntSort>> mkSolver(ctx: Context): USolverBase<PythonType> {
        val (translator, decoder) = buildTranslatorAndLazyDecoder(ctx)
        val softConstraintsProvider = USoftConstraintsProvider<PythonType, KIntSort>(ctx)
        val solver = KZ3Solver(ctx)
//        solver.configure { setZ3Option("timeout", 1) }
        return USolverBase(ctx, solver, UTypeSolver(typeSystem),  translator, decoder, softConstraintsProvider)
    }

    override fun mkTypeSystem(ctx: UContext<KIntSort>): UTypeSystem<PythonType> {
        return typeSystem
    }

    override fun <Context : UContext<KIntSort>> mkSizeExprProvider(ctx: Context): USizeExprProvider<KIntSort> {
        return UInt32SizeExprProvider(ctx)
    }
}
