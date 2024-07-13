package org.usvm.machine

import io.ksmt.solver.KSolver
import io.ksmt.solver.z3.KZ3Solver
import io.ksmt.sort.KIntSort
import org.usvm.UComponents
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UInt32SizeExprProvider
import org.usvm.USizeExprProvider
import org.usvm.collection.array.length.UInputArrayLengthReading
import org.usvm.constraints.UPathConstraints
import org.usvm.machine.types.PythonType
import org.usvm.machine.types.PythonTypeSystem
import org.usvm.mkSizeExpr
import org.usvm.mkSizeLeExpr
import org.usvm.model.UModelBase
import org.usvm.model.UModelDecoder
import org.usvm.solver.UExprTranslator
import org.usvm.solver.USoftConstraintsProvider
import org.usvm.solver.USolverBase
import org.usvm.solver.USolverResult
import org.usvm.solver.UTypeSolver
import org.usvm.types.UTypeSystem
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class PyComponents(
    private val typeSystem: PythonTypeSystem,
    private val solverTimeout: Duration = 500.milliseconds,
) : UComponents<PythonType, KIntSort> {
    override val useSolverForForks: Boolean = true
    override fun <Context : UContext<KIntSort>> mkSolver(ctx: Context): USolverBase<PythonType> {
        val (translator, decoder) = buildTranslatorAndLazyDecoder(ctx)
        val solver = KZ3Solver(ctx)
        return PySolver(ctx, solver, UTypeSolver(typeSystem), translator, decoder, solverTimeout)
    }

    override fun mkTypeSystem(ctx: UContext<KIntSort>): UTypeSystem<PythonType> {
        return typeSystem
    }

    override fun <Context : UContext<KIntSort>> mkSizeExprProvider(ctx: Context): USizeExprProvider<KIntSort> {
        return UInt32SizeExprProvider(ctx)
    }

    override fun <Context : UContext<KIntSort>> mkSoftConstraintsProvider(
        ctx: Context,
    ): USoftConstraintsProvider<PythonType, KIntSort> {
        return PySoftConstraintsProvider(ctx)
    }
}

class PySolver<Type>(
    ctx: UContext<*>,
    smtSolver: KSolver<*>,
    typeSolver: UTypeSolver<Type>,
    translator: UExprTranslator<Type, *>,
    decoder: UModelDecoder<UModelBase<Type>>,
    timeout: Duration,
) : USolverBase<Type>(
    ctx,
    smtSolver,
    typeSolver,
    translator,
    decoder,
    timeout,
) {
    override fun check(query: UPathConstraints<Type>): USolverResult<UModelBase<Type>> {
        require(query is PyPathConstraints)
        val softConstraints = ctx.softConstraintsProvider<Type>().makeSoftConstraints(query) +
            query.pythonSoftConstraints
        return super.checkWithSoftConstraints(query, softConstraints)
    }
}

private const val BOUND_0 = 1
private const val BOUND_1 = 16
private const val BOUND_2 = 256
private const val BOUND_3 = 16_000
private const val BOUND_4 = 100_000

private val arraySizeBounds =
    listOf(BOUND_0, BOUND_1, BOUND_2, BOUND_3, BOUND_4)

class PySoftConstraintsProvider(
    ctx: UContext<KIntSort>,
) : USoftConstraintsProvider<PythonType, KIntSort>(ctx) {
    override fun transform(
        expr: UInputArrayLengthReading<PythonType, KIntSort>,
    ): UExpr<KIntSort> = computeSideEffect(expr) {
        with(ctx) {
            val addressIsNull = provide(expr.address)
            val arrayConstraints = arraySizeBounds.map {
                mkSizeLeExpr(expr, mkSizeExpr(it))
            }

            caches[expr] = addressIsNull + arrayConstraints.toSet()
        }
    }
}
