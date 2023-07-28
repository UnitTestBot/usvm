package org.usvm.solver

import io.ksmt.solver.KSolver
import io.ksmt.solver.KSolverStatus
import io.ksmt.utils.asExpr
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UHeapRef
import org.usvm.USizeExpr
import org.usvm.constraints.UEqualityConstraints
import org.usvm.constraints.UPathConstraints
import org.usvm.isFalse
import org.usvm.isTrue
import org.usvm.logger
import org.usvm.memory.UMemoryBase
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

open class USolverBase<Field, Type, Method>(
    protected val ctx: UContext,
    protected val smtSolver: KSolver<*>,
    protected val typeSolver: UTypeSolver<Type>,
    protected val translator: UExprTranslator<Field, Type>,
    protected val decoder: UModelDecoder<UMemoryBase<Field, Type, Method>, UModelBase<Field, Type>>,
    protected val softConstraintsProvider: USoftConstraintsProvider<Field, Type>,
) : USolver<UPathConstraints<Type>, UModelBase<Field, Type>>(), AutoCloseable {

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

        val processedConstraints = mutableSetOf<Pair<UHeapRef, UHeapRef>>()

        for ((ref1, disequalRefs) in constraints.referenceDisequalities.entries) {
            for (ref2 in disequalRefs) {
                if (!processedConstraints.contains(ref2 to ref1)) {
                    processedConstraints.add(ref1 to ref2)
                    val translatedRef1 = translator.translate(ref1)
                    val translatedRef2 = translator.translate(ref2)
                    smtSolver.assert(ctx.mkNot(ctx.mkEq(translatedRef1, translatedRef2)))
                }
            }
        }

        processedConstraints.clear()
        val translatedNull = translator.transform(ctx.nullRef)
        for ((ref1, disequalRefs) in constraints.nullableDisequalities.entries) {
            for (ref2 in disequalRefs) {
                if (!processedConstraints.contains(ref2 to ref1)) {
                    processedConstraints.add(ref1 to ref2)
                    val translatedRef1 = translator.translate(ref1)
                    val translatedRef2 = translator.translate(ref2)

                    val disequalityConstraint = ctx.mkNot(ctx.mkEq(translatedRef1, translatedRef2))
                    val nullConstraint1 = ctx.mkEq(translatedRef1, translatedNull)
                    val nullConstraint2 = ctx.mkEq(translatedRef2, translatedNull)
                    smtSolver.assert(ctx.mkOr(disequalityConstraint, ctx.mkAnd(nullConstraint1, nullConstraint2)))
                }
            }
        }
    }

    protected fun translateToSmt(pc: UPathConstraints<Type>) {
        translateEqualityConstraints(pc.equalityConstraints)
        translateLogicalConstraints(pc.logicalConstraints)
    }

    protected fun translateMinimizeConstraint(pc: UPathConstraints<Type>): MinimizeConstraint {
        if (pc.minimizeConstraints.isEmpty()) {
            return NoMinimizeConstraint
        }

        val constraintsCount = pc.minimizeConstraints.size
        val constraintExpr = pc.minimizeConstraints.asSequence()
            .map { translator.translate(it) }
            .reduce { acc, expr -> ctx.mkBvAddExpr(acc, expr) }

        val initialBound = mulNoOverflow(constraintsCount, PREFERRED_SIZE) ?: return NoMinimizeConstraint

        logger.debug { "Minimize size constraint: $constraintExpr -> $initialBound" }

        return BoundedMinimizeConstraint(constraintExpr, initialBound)
    }

    protected fun relaxMinimizeConstraint(constraint: MinimizeConstraint): MinimizeConstraint =
        when (constraint) {
            is NoMinimizeConstraint -> constraint
            is BoundedMinimizeConstraint -> {
                val relaxedBound = mulNoOverflow(constraint.bound, times = 2)

                logger.debug { "Relax size constraint: ${constraint.bound} -> $relaxedBound" }

                relaxedBound?.let { BoundedMinimizeConstraint(constraint.expr, it) } ?: NoMinimizeConstraint
            }
        }

    protected fun mkMinimizeConstraintExpr(constraint: MinimizeConstraint): UBoolExpr? =
        when (constraint) {
            is NoMinimizeConstraint -> null
            is BoundedMinimizeConstraint ->
                ctx.mkBvSignedLessOrEqualExpr(constraint.expr, ctx.mkSizeExpr(constraint.bound))
        }

    protected fun mulNoOverflow(value: Int, times: Int): Int? {
        if (value > Int.MAX_VALUE / times) return null
        return value * times
    }

    override fun check(query: UPathConstraints<Type>): USolverResult<UModelBase<Field, Type>> =
        internalCheck(query, useSoftConstraints = false)

    fun checkWithSoftConstraints(
        pc: UPathConstraints<Type>,
    ) = internalCheck(pc, useSoftConstraints = true)


    private fun internalCheck(
        pc: UPathConstraints<Type>,
        useSoftConstraints: Boolean,
    ): USolverResult<UModelBase<Field, Type>> {
        if (pc.isFalse) {
            return UUnsatResult()
        }

        smtSolver.withAssertionsScope {
            translateToSmt(pc)

            val minimizeConstraint = MutableMinimizeConstraint(translateMinimizeConstraint(pc))

            val softConstraints = mutableListOf<UBoolExpr>()
            if (useSoftConstraints) {
                pc.logicalConstraints.flatMapTo(softConstraints) {
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
                val kModel = when (internalCheckWithSoftConstraints(softConstraints, minimizeConstraint)) {
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
                            uModel.heap,
                            typeResult.model,
                            uModel.mocks
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
        minimizeConstraint: MutableMinimizeConstraint,
    ): KSolverStatus {
        if (softConstraints.isEmpty() && minimizeConstraint.constraint is NoMinimizeConstraint) {
            return smtSolver.check()
        }

        while (true) {
            val minimizeConstraintExpr = mkMinimizeConstraintExpr(minimizeConstraint.constraint)

            val status = softConstraints.withNotNullElement(minimizeConstraintExpr) {
                smtSolver.checkWithAssumptions(softConstraints)
            }

            if (status != KSolverStatus.UNSAT) return status

            val unsatCore = smtSolver.unsatCore().toHashSet()
            if (unsatCore.isEmpty()) return KSolverStatus.UNSAT

            // Try relax minimize constraint first
            if (minimizeConstraintExpr in unsatCore) {
                minimizeConstraint.constraint = relaxMinimizeConstraint(minimizeConstraint.constraint)
                continue
            }

            softConstraints.removeAll { it in unsatCore }
        }
    }

    private inline fun <T> KSolver<*>.withAssertionsScope(block: KSolver<*>.() -> T): T = try {
        push()
        block()
    } finally {
        pop()
    }

    private inline fun <R, T> MutableList<T>.withNotNullElement(element: T?, block: () -> R): R = try {
        if (element != null) {
            add(element)
        }
        block()
    } finally {
        if (element != null) {
            removeLast()
        }
    }

    fun emptyModel(): UModelBase<Field, Type> =
        (checkWithSoftConstraints(UPathConstraints(ctx)) as USatResult<UModelBase<Field, Type>>).model

    override fun close() {
        smtSolver.close()
    }

    class MutableMinimizeConstraint(var constraint: MinimizeConstraint)

    sealed interface MinimizeConstraint

    object NoMinimizeConstraint : MinimizeConstraint

    class BoundedMinimizeConstraint(val expr: USizeExpr, val bound: Int): MinimizeConstraint

    companion object {
        // TODO: options
        /**
         * -1 means no threshold
         */
        val ITERATIONS_THRESHOLD = -1
        val INFINITE_ITERATIONS = -1

        const val PREFERRED_SIZE = 10
    }
}
