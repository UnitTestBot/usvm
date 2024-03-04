package org.usvm.machine

import io.ksmt.solver.KSolver
import io.ksmt.solver.z3.KZ3Solver
import io.ksmt.sort.KIntSort
import org.usvm.*
import org.usvm.constraints.UPathConstraints
import org.usvm.machine.types.PythonType
import org.usvm.machine.types.PythonTypeSystem
import org.usvm.model.UModelBase
import org.usvm.model.UModelDecoder
import org.usvm.solver.*
import org.usvm.types.UTypeSystem
import kotlin.time.Duration.Companion.milliseconds

class PyComponents(
    private val typeSystem: PythonTypeSystem
): UComponents<PythonType, KIntSort> {
    override val useSolverForForks: Boolean = true
    override fun <Context : UContext<KIntSort>> mkSolver(ctx: Context): USolverBase<PythonType> {
        val (translator, decoder) = buildTranslatorAndLazyDecoder(ctx)
        val solver = KZ3Solver(ctx)
        return PySolver(ctx, solver, UTypeSolver(typeSystem),  translator, decoder)
    }

    override fun mkTypeSystem(ctx: UContext<KIntSort>): UTypeSystem<PythonType> {
        return typeSystem
    }

    override fun <Context : UContext<KIntSort>> mkSizeExprProvider(ctx: Context): USizeExprProvider<KIntSort> {
        return UInt32SizeExprProvider(ctx)
    }
}

class PySolver<Type>(
    ctx: UContext<*>,
    smtSolver: KSolver<*>,
    typeSolver: UTypeSolver<Type>,
    translator: UExprTranslator<Type, *>,
    decoder: UModelDecoder<UModelBase<Type>>,
) : USolverBase<Type>(
    ctx, smtSolver, typeSolver, translator, decoder, 500.milliseconds
) {
    override fun check(query: UPathConstraints<Type>): USolverResult<UModelBase<Type>> {
        val softConstraints = ctx.softConstraintsProvider<Type>().makeSoftConstraints(query) + query.pythonSoftConstraints
        return super.checkWithSoftConstraints(query, softConstraints)
    }
}