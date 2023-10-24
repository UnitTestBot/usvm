package org.usvm.solver

import io.ksmt.solver.KSolver
import io.ksmt.solver.KSolverStatus
import io.ksmt.utils.asExpr
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.constraints.UEqualityConstraints
import org.usvm.constraints.UPathConstraints
import org.usvm.isFalse
import org.usvm.isStaticHeapRef
import org.usvm.isTrue
import org.usvm.model.UModelBase
import org.usvm.model.UModelDecoder

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
    protected val softConstraintsProvider: USoftConstraintsProvider<Type, *>,
) : USolver<UPathConstraints<Type>, UModelBase<Type>>(), AutoCloseable {

    protected fun translateLogicalConstraints(constraints: Iterable<UBoolExpr>) {
        for (constraint in constraints) {
            val translated = translator.translate(constraint)
            smtSolver.assert(translated)
        }
    }

    protected fun translateEqualityConstraints(constraints: UEqualityConstraints) {
        var index = 1

        val nullRepr = constraints.equalReferences.find(ctx.nullRef)
        for (ref in constraints.distinctReferences) {
            // Static refs are already translated as a values of an uninterpreted sort
            if (isStaticHeapRef(ref)) {
                continue
            }

            val refIndex = if (ref == nullRepr) 0 else index++
            val translatedRef = translator.translate(ref)
            val preInterpretedValue = ctx.mkUninterpretedSortValue(ctx.addressSort, refIndex)
            smtSolver.assert(ctx.mkEq(translatedRef, preInterpretedValue))
        }

        for ((key, value) in constraints.equalReferences) {
            val translatedLeft = translator.translate(key)
            val translatedRight = translator.translate(value)
            smtSolver.assert(ctx.mkEq(translatedLeft, translatedRight))
        }

        constraints.forEachInGraph(constraints.referenceDisequalities) { ref1, ref2 ->
            val translatedRef1 = translator.translate(ref1)
            val translatedRef2 = translator.translate(ref2)
            smtSolver.assert(ctx.mkNot(ctx.mkEq(translatedRef1, translatedRef2)))
        }

        val translatedNull = translator.transform(ctx.nullRef)
        constraints.forEachInGraph(constraints.nullableDisequalities) { ref1, ref2 ->
            val translatedRef1 = translator.translate(ref1)
            val translatedRef2 = translator.translate(ref2)

            val disequalityConstraint = ctx.mkNot(ctx.mkEq(translatedRef1, translatedRef2))
            val nullConstraint1 = ctx.mkEq(translatedRef1, translatedNull)
            val nullConstraint2 = ctx.mkEq(translatedRef2, translatedNull)
            smtSolver.assert(ctx.mkOr(disequalityConstraint, ctx.mkAnd(nullConstraint1, nullConstraint2)))
        }
    }

    protected fun translateToSmt(pc: UPathConstraints<Type>) {
        translateEqualityConstraints(pc.equalityConstraints)
        translateLogicalConstraints(pc.numericConstraints.constraints().asIterable())
        translateLogicalConstraints(pc.logicalConstraints)
    }

    override fun check(query: UPathConstraints<Type>): USolverResult<UModelBase<Type>> =
        internalCheck(query, useSoftConstraints = false)

    fun checkWithSoftConstraints(
        pc: UPathConstraints<Type>,
    ) = internalCheck(pc, useSoftConstraints = true)


    private fun internalCheck(
        pc: UPathConstraints<Type>,
        useSoftConstraints: Boolean,
    ): USolverResult<UModelBase<Type>> {
        if (pc.isFalse) {
            return UUnsatResult()
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
                    KSolverStatus.UNSAT -> return UUnsatResult()
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
                        .forEach(smtSolver::assert)

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
            status = smtSolver.checkWithAssumptions(softConstraints)

            while (status == KSolverStatus.UNSAT) {
                val unsatCore = smtSolver.unsatCore().toHashSet()
                if (unsatCore.isEmpty()) break
                softConstraints.removeAll { it in unsatCore }
                status = smtSolver.checkWithAssumptions(softConstraints)
            }
        } else {
            status = smtSolver.check()
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
