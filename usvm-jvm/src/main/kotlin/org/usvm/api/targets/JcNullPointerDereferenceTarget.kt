package org.usvm.api.targets

import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcInst
import org.usvm.UHeapRef
import org.usvm.machine.state.JcState

class JcNullPointerDereferenceTarget(method: JcMethod, inst: JcInst) : JcTarget(method, inst) {

    override fun onNullPointerDereference(state: JcState, ref: UHeapRef) {
        if (state.callStack.lastMethod() == method && state.currentStatement == statement) {
            visit(state)
        }
        super.onNullPointerDereference(state, ref)
    }
}
