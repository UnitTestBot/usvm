package org.usvm.interpreter

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.usvm.UCallStack
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.constraints.UPathConstraints
import org.usvm.language.Field
import org.usvm.language.Method
import org.usvm.language.ProgramException
import org.usvm.language.SampleType
import org.usvm.language.Stmt
import org.usvm.memory.UMemoryBase
import org.usvm.model.UModel
import org.usvm.UState

class SampleState(
    pathConstraints: UPathConstraints<SampleType>,
    memory: UMemoryBase<Field<*>, SampleType, Method<*>>,
    callStack: UCallStack<Method<*>, Stmt> = UCallStack(),
    models: List<UModel> = listOf(),
    path: PersistentList<Stmt> = persistentListOf(),
    var returnRegister: UExpr<out USort>? = null,
    var exceptionRegister: ProgramException? = null,
) : UState<SampleType, Field<*>, Method<*>, Stmt>(
    pathConstraints,
    memory,
    callStack,
    models,
    path
) {

    override fun clone(newConstraints: UPathConstraints<SampleType>?): SampleState {
        val clonedConstraints = newConstraints ?: pathConstraints.clone()
        return SampleState(
            clonedConstraints,
            memory.clone(clonedConstraints.typeConstraints),
            callStack.clone(),
            models,
            path,
            returnRegister,
            exceptionRegister
        )
    }

    companion object {
        fun create(ctx: UContext): SampleState {
            val pc = UPathConstraints<SampleType>(ctx)
            return SampleState(pc, UMemoryBase(ctx, pc.typeConstraints))
        }
    }
}