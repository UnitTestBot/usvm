package org.usvm

import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.usvm.ps.DfsPathSelector

class JcMachine(
    cp: JcClasspath,
) : UMachine<JcState, JcMethod>() {
    private val typeSystem = JcTypeSystem(cp)
    private val components = JcComponents(typeSystem)
    private val ctx = UContext(components)

    private val interpreter = JcInterpreter(cp, ctx)

//    fun analyze(method: JcMethod) {
//
//    }

    override fun getInterpreter(target: JcMethod): UInterpreter<JcState> =
        interpreter

    override fun getPathSelector(target: JcMethod): UPathSelector<JcState> {
        val ps = DfsPathSelector<JcState>()
        val state = getInitialState(target)
        ps.add(sequenceOf(state))
        return ps
    }

    private fun getInitialState(method: JcMethod): JcState {
        TODO()
    }
}