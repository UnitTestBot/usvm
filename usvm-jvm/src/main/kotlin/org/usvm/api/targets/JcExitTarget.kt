package org.usvm.api.targets

import org.usvm.machine.state.JcState

/**
 * [JcTarget] which is reached when a state is terminated. Can be used to
 * force termination of the states, which have visited [JcExitTarget]'s parent targets.
 */
class JcExitTarget : JcTarget() {
    override fun onStateTerminated(state: JcState) {
        visit(state)
        super.onStateTerminated(state)
    }
}
