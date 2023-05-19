package org.usvm

import org.jacodb.analysis.impl.JcApplicationGraphImpl
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.impl.features.HierarchyExtensionImpl
import org.jacodb.impl.features.SyncUsagesExtension
import org.usvm.ps.DfsPathSelector
import org.usvm.state.JcState

class JcMachine(
    cp: JcClasspath,
) : UMachine<JcState, JcMethod>() {
    private val typeSystem = JcTypeSystem(cp)
    private val components = JcComponents(typeSystem)
    private val ctx = UContext(components)
    private val applicationGraph = JcApplicationGraph(cp)

    private val interpreter = JcInterpreter(cp, ctx, applicationGraph)

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