package org.usvm

import org.ksmt.solver.KSolver
import org.ksmt.solver.KSolverStatus

sealed interface USolverResult<T>

open class USatResult<Model>(
    val model: Model
) : USolverResult<Model>

open class UUnsatResult<Model> : USolverResult<Model>

open class UUnknownResult<Model> : USolverResult<Model>

abstract class USolver<Memory, PathCondition, Model> {
    abstract fun check(memory: Memory, pc: PathCondition): USolverResult<Model>
}

open class USolverBase<Field, Type, Method>(
    protected val ctx: UContext,
    protected val solver: KSolver<*>,
    protected val translator: UExprTranslator<Field, Type>,
    protected val decoder: UModelDecoder<UMemoryBase<Field, Type, Method>, UModelBase<Field, Type>>,
) : USolver<UMemoryBase<Field, Type, Method>, UPathCondition, UModelBase<Field, Type>>() {

    override fun check(memory: UMemoryBase<Field, Type, Method>, pc: UPathCondition): USolverResult<UModelBase<Field, Type>> {
        if (pc.isFalse) {
            return UUnsatResult()
        }
        solver.push()

        for (constraint in pc) {
            val translated = translator.translate(constraint)
            solver.assert(translated)
        }

        val status = solver.check()
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
