package org.usvm.solver

import org.ksmt.solver.KSolver
import org.ksmt.solver.KSolverStatus
import org.usvm.UContext
import org.usvm.UPathCondition
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
    // TODO is it a good idea to lift up information about soft constraints into this interface?
    abstract fun check(memory: Memory, pc: PathCondition, useSoftConstraints: Boolean): USolverResult<Model>
}

open class USolverBase<Field, Type, Method>(
    protected val ctx: UContext,
    protected val solver: KSolver<*>,
    protected val translator: UExprTranslator<Field, Type>,
    protected val decoder: UModelDecoder<UMemoryBase<Field, Type, Method>, UModelBase<Field, Type>>,
    protected val softConstraintsProvider: USoftConstraintsProvider<Field, Type>,
) : USolver<UMemoryBase<Field, Type, Method>, UPathCondition, UModelBase<Field, Type>>() {

    internal fun checkWithSoftConstraints(
        memory: UMemoryBase<Field, Type, Method>,
        pc: UPathCondition,
    ) = check(memory, pc, useSoftConstraints = true)

    override fun check(
        memory: UMemoryBase<Field, Type, Method>,
        pc: UPathCondition,
        useSoftConstraints: Boolean,
    ): USolverResult<UModelBase<Field, Type>> {
        if (pc.isFalse) {
            return UUnsatResult()
        }

        solver.push()

        for (constraint in pc) {
            val translated = translator.translate(constraint)
            solver.assert(translated)
        }

        var status: KSolverStatus

        if (useSoftConstraints) {
            // TODO fold with contradiction search?
            //      additional caches?
            val softConstraints = pc.map {
                val softConstraint = softConstraintsProvider.provide(it)
                translator.translate(softConstraint)
            }

            status = solver.checkWithAssumptions(softConstraints)

            while (status == KSolverStatus.UNSAT) {
                val unsatCore = solver.unsatCore()

                if (unsatCore.isEmpty()) break

                val newSoftConstraints = softConstraints.filterNot { it in unsatCore }
                status = solver.checkWithAssumptions(newSoftConstraints)
            }
        } else {
            status = solver.check()
        }

        if (status != KSolverStatus.SAT) {
            solver.pop()

            return if (status == KSolverStatus.UNSAT) {
                UUnsatResult()
            } else {
                UUnknownResult()
            }
        }

        val kModel = solver.model().detach()
        val uModel = decoder.decode(memory, kModel)

        solver.pop()

        return USatResult(uModel)
    }
}
