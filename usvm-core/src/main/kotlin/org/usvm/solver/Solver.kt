package org.usvm.solver

import io.ksmt.expr.KExpr
import io.ksmt.solver.KSolver
import io.ksmt.solver.KSolverStatus
import io.ksmt.sort.KBoolSort
import io.ksmt.utils.asExpr
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UMachineOptions
import org.usvm.constraints.ConstraintSource
import org.usvm.constraints.PathConstraintsUnsatCore
import org.usvm.constraints.UPathConstraints
import org.usvm.constraints.UnknownConstraintSource
import org.usvm.isFalse
import org.usvm.isTrue
import org.usvm.model.UModelBase
import org.usvm.model.UModelDecoder
import kotlin.time.Duration.Companion.INFINITE
import kotlin.time.Duration.Companion.milliseconds

sealed interface USolverResult<out T>

open class USatResult<out Model>(
    val model: Model,
) : USolverResult<Model>

open class UUnsatResult<Model>(
    val core: PathConstraintsUnsatCore
) : USolverResult<Model>

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
    protected val softConstraintsProvider: USoftConstraintsProvider<Type, *>,
    protected val options: UMachineOptions? = null
) : USolver<UPathConstraints<Type>, UModelBase<Type>>(), AutoCloseable {

    private val constraintSources = hashMapOf<KExpr<KBoolSort>, ConstraintSource>()

    private fun assertConstraint(constraint: KExpr<KBoolSort>, source: ConstraintSource) {
        smtSolver.assertAndTrack(constraint)
        constraintSources[constraint] = source
    }

    protected fun translateToSmt(pc: UPathConstraints<Type>) {
        pc.equalityConstraints.translateAndAssert(translator, ::assertConstraint)
        pc.numericConstraints.translateAndAssert(translator, ::assertConstraint)
        pc.logicalConstraints.translateAndAssert(translator, ::assertConstraint)
    }

    override fun check(query: UPathConstraints<Type>): USolverResult<UModelBase<Type>> =
        internalCheckWithCleanup(query, useSoftConstraints = false)

    fun checkWithSoftConstraints(pc: UPathConstraints<Type>): USolverResult<UModelBase<Type>> =
        internalCheckWithCleanup(pc, useSoftConstraints = options?.solverUseSoftConstraints ?: true)

    protected fun cleanup() {
        constraintSources.clear()
    }

    private fun internalCheckWithCleanup(
        pc: UPathConstraints<Type>,
        useSoftConstraints: Boolean,
    ): USolverResult<UModelBase<Type>> = try {
        internalCheck(pc, useSoftConstraints)
    } finally {
        cleanup()
    }

    private fun internalCheck(
        pc: UPathConstraints<Type>,
        useSoftConstraints: Boolean,
    ): USolverResult<UModelBase<Type>> {
        if (pc.isFalse) {
            return UUnsatResult(pc.unsatCore())
        }

        smtSolver.withAssertionsScope {
            translateToSmt(pc)

            val softConstraints = mutableListOf<UBoolExpr>()
            if (useSoftConstraints) {
                val softConstraintSources = pc.logicalConstraints.asSequence() + pc.numericConstraints.constraints()
                softConstraintSources.flatMapTo(softConstraints) {
                    softConstraintsProvider
                        .provide(it)
                        .map(translator::translate)
                        .filterNot(UBoolExpr::isFalse)
                }
            }

            // DPLL(T)-like solve procedure
            var iter = 0
            @Suppress("KotlinConstantConditions")
            do {
                iter++

                // first, get a model from the SMT solver
                val kModel = when (internalCheckWithSoftConstraints(softConstraints)) {
                    KSolverStatus.SAT -> smtSolver.model().detach()
                    KSolverStatus.UNSAT -> {
                        val unsatCore = smtSolver.unsatCore()
                        val resolvedCore = unsatCore.map { it to (constraintSources[it] ?: UnknownConstraintSource) }
                        return UUnsatResult(PathConstraintsUnsatCore("SMT", resolvedCore))
                    }
                    KSolverStatus.UNKNOWN -> return UUnknownResult()
                }

                // second, decode it unto uModel
                val uModel = decoder.decode(kModel)

                // find interpretations of type constraints

                val isExprToInterpretation = kModel.declarations.mapNotNull { decl ->
                    translator.declToIsExpr[decl]?.let { isSubtypeExpr ->
                        val expr = decl.apply(emptyList())
                        isSubtypeExpr to kModel.eval(expr, isComplete = true).asExpr(ctx.boolSort).isTrue
                    }
                }

                // third, build a type solver query
                val typeSolverQuery = TypeSolverQuery(
                    symbolicToConcrete = { uModel.eval(it) as UConcreteHeapRef },
                    symbolicRefToTypeRegion = pc.typeConstraints.symbolicRefToTypeRegion,
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
                        .forEach {
                            assertConstraint(it, UnknownConstraintSource)
                        }

                    is UUnknownResult -> return UUnknownResult()
                    is UUnsatResult -> return UUnsatResult(typeResult.core)
                }
            } while (iter < ITERATIONS_THRESHOLD || ITERATIONS_THRESHOLD == INFINITE_ITERATIONS)

            return UUnsatResult(PathConstraintsUnsatCore("SMT Unknown", emptyList()))
        }
    }

    private fun internalCheckWithSoftConstraints(
        softConstraints: MutableList<UBoolExpr>,
    ): KSolverStatus {
        val timeout = options?.solverQueryTimeoutMs?.milliseconds ?: INFINITE
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
        (checkWithSoftConstraints(UPathConstraints(ctx)) as USatResult<UModelBase<Type>>).model

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
