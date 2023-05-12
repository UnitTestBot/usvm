package org.usvm.solver

import io.ksmt.solver.KSolver
import io.ksmt.solver.KSolverStatus
import org.usvm.UBoolExpr
import org.usvm.UContext
import org.usvm.UHeapRef
import org.usvm.constraints.UEqualityConstraints
import org.usvm.constraints.UPathConstraints
import org.usvm.memory.UMemoryBase
import org.usvm.model.UModelBase
import org.usvm.model.UModelDecoder

sealed interface USolverResult<T>

open class USatResult<Model>(
    val model: Model,
) : USolverResult<Model>

open class UUnsatResult<Model> : USolverResult<Model>

open class UUnknownResult<Model> : USolverResult<Model>

abstract class USolver<Memory, PathCondition, Model> {
    // TODO make global option for that?
    abstract fun check(pc: PathCondition, useSoftConstraints: Boolean): USolverResult<Model>
}

open class USolverBase<Field, Type, Method>(
    protected val ctx: UContext,
    protected val smtSolver: KSolver<*>,
    protected val translator: UExprTranslator<Field, Type>,
    protected val decoder: UModelDecoder<UMemoryBase<Field, Type, Method>, UModelBase<Field, Type>>,
    protected val softConstraintsProvider: USoftConstraintsProvider<Field, Type>,
) : USolver<UMemoryBase<Field, Type, Method>, UPathConstraints<Type>, UModelBase<Field, Type>>() {

    protected fun translateSmtConstraints(constraints: Iterable<UBoolExpr>) {
        for (constraint in constraints) {
            val translated = translator.translate(constraint)
            smtSolver.assert(translated)
        }
    }

    protected fun translateEqualityConstraints(constraints: UEqualityConstraints) {
        var index = 1
        for (ref in constraints.distinctReferences) {
            val refIndex = if (ref == ctx.nullRef) 0 else index++
            val translatedRef = translator.translate(ref)
            val preInterpretedValue = ctx.mkUninterpretedSortValue(ctx.addressSort, refIndex)
            smtSolver.assert(ctx.mkEq(translatedRef, preInterpretedValue))
        }

        for (equality in constraints.equalReferences) {
            val translatedLeft = translator.translate(equality.key)
            val translatedRight = translator.translate(equality.value)
            smtSolver.assert(ctx.mkEq(translatedLeft, translatedRight))
        }

        val processedConstraints = mutableSetOf<Pair<UHeapRef, UHeapRef>>()
        for (mapEntry in constraints.referenceDisequalities.entries) {
            val ref1 = mapEntry.key
            for (ref2 in mapEntry.value) {
                if (!processedConstraints.contains(ref2 to ref1)) {
                    processedConstraints.add(ref1 to ref2)
                    val translatedRef1 = translator.translate(ref1)
                    val translatedRef2 = translator.translate(ref2)
                    smtSolver.assert(ctx.mkNot(ctx.mkEq(translatedRef1, translatedRef2)))
                }
            }
        }
    }

    protected fun translateToSmt(pc: UPathConstraints<Type>) {
        translateEqualityConstraints(pc.equalityConstraints)
        translateSmtConstraints(pc.logicalConstraints)
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
}
