package org.usvm.solver

import io.ksmt.solver.KSolver
import io.ksmt.solver.KSolverStatus
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
    // TODO make global option for that?
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
            var softConstraints = pc.flatMap {
                softConstraintsProvider
                    .provide(it)
                    .map { sc -> translator.translate(sc) }
            }

            status = solver.checkWithAssumptions(softConstraints)

            while (status == KSolverStatus.UNSAT) {
                val unsatCore = solver.unsatCore()

                if (unsatCore.isEmpty()) break

                softConstraints = softConstraints.filterNot { it in unsatCore }
                status = solver.checkWithAssumptions(softConstraints)
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
