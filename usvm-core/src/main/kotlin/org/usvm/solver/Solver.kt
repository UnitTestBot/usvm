package org.usvm.solver

import io.ksmt.solver.KSolver
import io.ksmt.solver.KSolverStatus
import io.ksmt.utils.asExpr
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.constraints.UPathConstraints
import org.usvm.isFalse
import org.usvm.isTrue
import org.usvm.model.UModelBase
import org.usvm.model.UModelDecoder
import org.usvm.utils.ensureSat
import kotlin.time.Duration

sealed interface USolverResult<out T>

open class USatResult<out Model>(
    val model: Model,
) : USolverResult<Model>

open class UUnsatResult<Model> : USolverResult<Model>

open class UUnknownResult<Model> : USolverResult<Model>

abstract class USolver<in Query, out Model> {
    abstract fun check(query: Query): USolverResult<Model>
}

open class USolverBase<Type>(
    protected val ctx: UContext<*>,
    protected val smtSolver: KSolver<*>,
    protected val typeSolver: UTypeSolver<Type>,
    protected val translator: UExprTranslator<Type, *>,
    protected val decoder: UModelDecoder<UModelBase<Type>>,
    // TODO this timeout must not exceed time budget for the MUT
    private val timeout: Duration
) : USolver<UPathConstraints<Type>, UModelBase<Type>>(), AutoCloseable {

    override fun check(query: UPathConstraints<Type>): USolverResult<UModelBase<Type>> =
        internalCheck(query, softConstraints = emptyList())

    fun checkWithSoftConstraints(
        pc: UPathConstraints<Type>,
        softConstraints: Iterable<UBoolExpr>
    ): USolverResult<UModelBase<Type>> = internalCheck(pc, softConstraints)

    private fun internalCheck(
        pc: UPathConstraints<Type>,
        softConstraints: Iterable<UBoolExpr>,
    ): USolverResult<UModelBase<Type>> {
        if (pc.isFalse) {
            return UUnsatResult()
        }

        smtSolver.withAssertionsScope {
            val assertions = pc.constraints(translator).toList()
            smtSolver.assert(assertions)

            val translatedSoftConstraints = softConstraints
                .asSequence()
                .map(translator::translate)
                .filterNot(UBoolExpr::isFalse)
                .toMutableList()

            // DPLL(T)-like solve procedure
            var iter = 0
            @Suppress("KotlinConstantConditions")
            do {
                iter++

                // first, get a model from the SMT solver
                val kModel = when (internalCheckWithSoftConstraints(translatedSoftConstraints)) {
                    KSolverStatus.SAT -> smtSolver.model().detach()
                    KSolverStatus.UNSAT -> return UUnsatResult()
                    KSolverStatus.UNKNOWN -> return UUnknownResult()
                }

                // second, decode it unto uModel
                val uModel = decoder.decode(kModel, assertions)

                // find interpretations of type constraints

                val isExprToInterpretation = kModel.declarations.mapNotNull { decl ->
                    translator.declToIsExpr[decl]?.let { isSubtypeExpr ->
                        val expr = decl.apply(emptyList())
                        isSubtypeExpr to kModel.eval(expr, isComplete = true).asExpr(ctx.boolSort).isTrue
                    }
                }

                // third, build a type solver query
                val typeSolverQuery = TypeSolverQuery(
                    inputToConcrete = { uModel.eval(it) as UConcreteHeapRef },
                    inputRefToTypeRegion = pc.typeConstraints.inputRefToTypeRegion,
                    isExprToInterpretation = isExprToInterpretation,
                )

                // fourth, check it satisfies typeConstraints
                when (val typeResult = typeSolver.check(typeSolverQuery)) {
                    is USatResult -> return USatResult(
                        UModelBase(
                            ctx,
                            uModel.stack,
                            typeResult.model,
                            uModel.mocker,
                            uModel.regions,
                            uModel.nullRef
                        )
                    )

                    // in case of failure, assert reference disequality expressions
                    is UTypeUnsatResult<Type> -> typeResult.conflictLemmas
                        .map(translator::translate)
                        .let { smtSolver.assert(it) }

                    is UUnknownResult -> return UUnknownResult()
                    is UUnsatResult -> return UUnsatResult()
                }
            } while (iter < ITERATIONS_THRESHOLD || ITERATIONS_THRESHOLD == INFINITE_ITERATIONS)

            return UUnsatResult()
        }
    }

    private fun internalCheckWithSoftConstraints(
        softConstraints: MutableList<UBoolExpr>,
    ): KSolverStatus {
        var status: KSolverStatus
        if (softConstraints.isNotEmpty()) {
            status = smtSolver.checkWithAssumptions(softConstraints, timeout)

            while (status == KSolverStatus.UNSAT) {
                val unsatCore = smtSolver.unsatCore().toHashSet()
                if (unsatCore.isEmpty()) break
                softConstraints.removeAll { it in unsatCore }
                status = smtSolver.checkWithAssumptions(softConstraints, timeout)
            }
        } else {
            status = smtSolver.check(timeout)
        }
        return status
    }

    private inline fun <T> KSolver<*>.withAssertionsScope(block: KSolver<*>.() -> T): T = try {
        push()
        block()
    } finally {
        pop()
    }

    fun emptyModel(): UModelBase<Type> =
        check(UPathConstraints(ctx, ctx.defaultOwnership)).ensureSat().model

    override fun close() {
        smtSolver.close()
    }

    companion object {
        // TODO: options
        /**
         * -1 means no threshold
         */
        val ITERATIONS_THRESHOLD = -1
        val INFINITE_ITERATIONS = -1
    }
}
