package org.usvm.machine.state

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.jacodb.api.JcField
import org.jacodb.api.JcMethod
import org.jacodb.api.JcType
import org.jacodb.api.cfg.JcInst
import org.usvm.UCallStack
import org.usvm.UContext
import org.usvm.UState
import org.usvm.constraints.UPathConstraints
import org.usvm.machine.JcContext
import org.usvm.memory.UMemoryBase
import org.usvm.model.UModelBase

class JcState(
    ctx: UContext,
    callStack: UCallStack<JcMethod, JcInst> = UCallStack(),
    pathConstraints: UPathConstraints<JcType> = UPathConstraints(ctx),
    memory: UMemoryBase<JcField, JcType, JcMethod> = UMemoryBase(ctx, pathConstraints.typeConstraints),
    models: List<UModelBase<JcField, JcType>> = listOf(),
    path: PersistentList<JcInst> = persistentListOf(),
    var methodResult: JcMethodResult = JcMethodResult.NoCall,
) : UState<JcType, JcField, JcMethod, JcInst>(
    ctx,
    callStack,
    pathConstraints,
    memory,
    models,
    path
) {
    override fun clone(newConstraints: UPathConstraints<JcType>?): JcState {
        val clonedConstraints = newConstraints ?: pathConstraints.clone()
        return JcState(
            pathConstraints.ctx,
            callStack.clone(),
            clonedConstraints,
            memory.clone(clonedConstraints.typeConstraints),
            models,
            path,
            methodResult,
        )
    }

    override val isExceptional: Boolean
        get() = methodResult is JcMethodResult.Exception
}
