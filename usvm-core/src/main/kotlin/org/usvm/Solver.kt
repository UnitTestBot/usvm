package org.usvm

import org.ksmt.solver.KSolver
import org.ksmt.solver.KSolverStatus

sealed interface USolverResult<T>

open class USolverSat<Model>(
    val model: Model
) : USolverResult<Model>

open class USolverUnsat<Model> : USolverResult<Model>

open class USolverUnknown<Model> : USolverResult<Model>

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
            return USolverUnsat()
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
                USolverUnsat()
            } else {
                USolverUnknown()
            }
        }
        val model = solver.model().detach()
        solver.pop()

        val uModel = decoder.decode(memory, model)
        return USolverSat(uModel)
    }
}
