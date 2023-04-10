package org.usvm.concrete.interpreter

import org.ksmt.solver.KSolver
import org.ksmt.solver.KSolverStatus
import org.usvm.UContext
import org.usvm.UExprTranslator
import org.usvm.UMemoryBase
import org.usvm.UModelBase
import org.usvm.UModelDecoder
import org.usvm.UModelDecoderBase
import org.usvm.UPathCondition

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
    val ctx: UContext,
    val solver: KSolver<*>,
    val translator: UExprTranslator<Field, Type>,
    val evaluator: UModelDecoder<Type, UMemoryBase<Field, Type, Method>, UModelBase<Field, Type>>,
) : USolver<UMemoryBase<Field, Type, Method>, UPathCondition, UModelBase<Field, Type>>() {

    override fun check(memory: UMemoryBase<Field, Type, Method>, pc: UPathCondition): USolverResult<UModelBase<Field, Type>> {
        if (pc.isFalse) {
            return USolverUnsat()
        }
        solver.push()

        for (constraint in pc) {
            val translated = translator.apply(constraint)
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

        val uModel = evaluator.decode(memory, model)
        return USolverSat(uModel)
    }
}