package org.usvm.solver

import io.ksmt.solver.KSolver
import io.ksmt.solver.KSolverStatus
import org.usvm.UBoolExpr
import org.usvm.UContext
import org.usvm.UHeapRef
import org.usvm.constraints.UEqualityConstraints
import org.usvm.constraints.UPathConstraints
import org.usvm.constraints.UTypeUnsatResult
import org.usvm.memory.UMemoryBase
import org.usvm.model.UModelBase
import org.usvm.model.UModelDecoder

sealed interface USolverResult<out T>

open class USatResult<out Model>(
    val model: Model,
) : USolverResult<Model>

open class UUnsatResult<Model> : USolverResult<Model>

open class UUnknownResult<Model> : USolverResult<Model>

abstract class USolver<in PathCondition, out Model> : AutoCloseable {
    abstract fun check(pc: PathCondition, useSoftConstraints: Boolean): USolverResult<Model>
}

open class USolverBase<Field, Type, Method>(
    protected val ctx: UContext,
    protected val smtSolver: KSolver<*>,
    protected val translator: UExprTranslator<Field, Type>,
    protected val decoder: UModelDecoder<UMemoryBase<Field, Type, Method>, UModelBase<Field, Type>>,
    protected val softConstraintsProvider: USoftConstraintsProvider<Field, Type>,
) : USolver<UPathConstraints<Type>, UModelBase<Field, Type>>() {
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

    internal fun checkWithSoftConstraints(
        pc: UPathConstraints<Type>,
    ) = check(pc, useSoftConstraints = true)

    override fun check(
        pc: UPathConstraints<Type>,
        useSoftConstraints: Boolean,
    ): USolverResult<UModelBase<Field, Type>> {
        if (pc.isFalse) {
            return UUnsatResult()
        }

        smtSolver.withAssertionsScope {
            translateToSmt(pc)

            val softConstraints = mutableListOf<UBoolExpr>()
            if (useSoftConstraints) {
                pc.logicalConstraints.flatMapTo(softConstraints) {
                    softConstraintsProvider
                        .provide(it)
                        .map { sc -> translator.translate(sc) }
                }
            }

            var iter = 0
            @Suppress("KotlinConstantConditions")
            do {
                iter++
                val status: KSolverStatus = internalCheckWithSoftConstraints(softConstraints)

                val kModel = when (status) {
                    KSolverStatus.SAT -> smtSolver.model().detach()
                    KSolverStatus.UNSAT -> return UUnsatResult()
                    KSolverStatus.UNKNOWN -> return UUnknownResult()
                }

                val uModel = decoder.decode(kModel)

                when (val typeResult = pc.typeConstraints.verify(uModel)) {
                    is USatResult -> return USatResult(
                        UModelBase(
                            ctx,
                            uModel.stack,
                            uModel.heap,
                            typeResult.model,
                            uModel.mocks
                        )
                    )

                    is UTypeUnsatResult<Type> -> typeResult.expressionsToAssert.forEach(smtSolver::assert)
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
                softConstraints.removeAll { it in unsatCore}
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

    fun emptyModel(): UModelBase<Field, Type> =
        (checkWithSoftConstraints(UPathConstraints(ctx)) as USatResult<UModelBase<Field, Type>>).model

    override fun close() {
        smtSolver.close()
    }

    companion object {
        /**
         * -1 means no threshold
         */
        val ITERATIONS_THRESHOLD = -1
        val INFINITE_ITERATIONS = -1
    }
}