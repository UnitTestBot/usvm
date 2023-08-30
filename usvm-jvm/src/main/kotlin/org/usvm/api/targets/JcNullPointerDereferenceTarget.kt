package org.usvm.api.targets

import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcInst
import org.usvm.UHeapRef
import org.usvm.machine.state.JcState

/**
 * [JcTarget] which is reached when null pointer is dereferenced in location ([method], [inst]).
 */
class JcNullPointerDereferenceTarget(private val method: JcMethod, private val inst: JcInst) : JcTarget(method to inst) {

    override fun onNullPointerDereference(state: JcState, ref: UHeapRef) {
        if (state.callStack.lastMethod() == method && state.currentStatement == inst) {
            visit(state)
        }
        super.onNullPointerDereference(state, ref)
    }
}
