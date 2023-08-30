package org.usvm.api.targets

import org.usvm.machine.state.JcState

class JcExitTarget : JcTarget() {
    override fun onStateTerminated(state: JcState) {
        visit(state)
        super.onStateTerminated(state)
    }
}
