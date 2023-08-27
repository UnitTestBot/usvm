package org.usvm.api.targets

import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcInst
import org.usvm.machine.state.JcState

class JcLocationTarget(method: JcMethod, inst: JcInst) : JcTarget(method, inst) {

    private fun hasReached(state: JcState): Boolean {
        return state.callStack.isNotEmpty() && method == state.callStack.lastMethod() && statement == state.currentStatement
    }

    override fun onState(parent: JcState, forks: Sequence<JcState>) {
        val reachedState = if (hasReached(parent)) parent else forks.find(::hasReached)
        if (reachedState != null) {
            visit(reachedState)
        }
        super.onState(parent, forks)
    }
}
