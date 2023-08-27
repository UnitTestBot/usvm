package org.usvm.api.targets

import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcInst
import org.usvm.UHeapRef
import org.usvm.UTarget
import org.usvm.machine.interpreter.JcInterpreterObserver
import org.usvm.machine.state.JcState

open class JcTarget(method: JcMethod, inst: JcInst) : UTarget<JcMethod, JcInst, JcTarget, JcState>(method, inst), JcInterpreterObserver {

    override fun onNullPointerDereference(state: JcState, ref: UHeapRef) {
        forEachChild { onNullPointerDereference(state, ref) }
    }
}
