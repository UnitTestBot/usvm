package org.usvm.machine

import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcInst
import org.usvm.PathSelectionStrategy
import org.usvm.api.targets.JcTarget
import org.usvm.machine.state.JcState
import org.usvm.statistics.distances.CallGraphStatistics
import org.usvm.statistics.distances.CfgStatistics

interface JcTargetWeighter {
    fun createWeighter(
        strategy: PathSelectionStrategy,
        applicationGraph: JcApplicationGraph,
        cfgStatistics: CfgStatistics<JcMethod, JcInst>,
        callGraphStatistics: CallGraphStatistics<JcMethod>
    ): (JcTarget, JcState) -> UInt?
}
