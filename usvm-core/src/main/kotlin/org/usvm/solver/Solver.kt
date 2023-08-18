package org.usvm.solver

import io.ksmt.solver.KSolver
import io.ksmt.solver.KSolverStatus
import org.usvm.UBoolExpr
import org.usvm.UContext
import org.usvm.UHeapRef
import org.usvm.constraints.UEqualityConstraints
import org.usvm.constraints.UPathConstraints
import org.usvm.memory.UMemory
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

open class USolverBase<Type, Method>(
    protected val ctx: UContext,
    protected val smtSolver: KSolver<*>,
    protected val translator: UExprTranslator<Type>,
    protected val decoder: UModelDecoder<UModelBase<Type>>,
    protected val softConstraintsProvider: USoftConstraintsProvider<Type>,
) : USolver<UPathConstraints<Type>, UModelBase<Type>>() {

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
    ): USolverResult<UModelBase<Type>> {
        if (pc.isFalse) {
            return UUnsatResult()
        }

        smtSolver.push()

        translateToSmt(pc)

        var status: KSolverStatus

        if (useSoftConstraints) {
            var softConstraints = pc.logicalConstraints.flatMap {
                softConstraintsProvider
                    .provide(it)
                    .map { sc -> translator.translate(sc) }
            }

            status = smtSolver.checkWithAssumptions(softConstraints)

            while (status == KSolverStatus.UNSAT) {
                val unsatCore = smtSolver.unsatCore()

                if (unsatCore.isEmpty()) break

                softConstraints = softConstraints.filterNot { it in unsatCore }
                status = smtSolver.checkWithAssumptions(softConstraints)
            }
        } else {
            status = smtSolver.check()
        }

        if (status != KSolverStatus.SAT) {
            smtSolver.pop()

            return if (status == KSolverStatus.UNSAT) {
                UUnsatResult()
            } else {
                UUnknownResult()
            }
        }

        val kModel = smtSolver.model().detach()
        val uModel = decoder.decode(kModel)

        smtSolver.pop()

        return USatResult(uModel)
    }

    fun emptyModel(): UModelBase<Type> =
        (checkWithSoftConstraints(UPathConstraints(ctx)) as USatResult<UModelBase<Type>>).model

    override fun close() {
        smtSolver.close()
    }
}