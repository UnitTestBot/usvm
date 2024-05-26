package org.usvm.machine

import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcInst
import org.usvm.PathSelectionStrategy
import org.usvm.UPathSelector
import org.usvm.api.targets.JcTarget
import org.usvm.forkblacklists.UForkBlackList
import org.usvm.machine.state.JcState
import org.usvm.ps.TargetWeight
import org.usvm.statistics.distances.CallGraphStatistics
import org.usvm.statistics.distances.CfgStatistics

interface JcTargetWeighter<T> where T : TargetWeight {
    fun createWeighter(
        strategy: PathSelectionStrategy,
        applicationGraph: JcApplicationGraph,
        cfgStatistics: CfgStatistics<JcMethod, JcInst>,
        callGraphStatistics: CallGraphStatistics<JcMethod>
    ): (JcTarget, JcState) -> T?
}

interface JcTargetBlackLister {
    fun createBlacklist(
        baseBlackList: UForkBlackList<JcState, JcInst>,
        applicationGraph: JcApplicationGraph,
        cfgStatistics: CfgStatistics<JcMethod, JcInst>,
        callGraphStatistics: CallGraphStatistics<JcMethod>
    ): UForkBlackList<JcState, JcInst>
}

interface JcPathSelectorProvider {
    fun createPathSelector(
        initialState: JcState,
        applicationGraph: JcApplicationGraph,
        cfgStatistics: CfgStatistics<JcMethod, JcInst>,
        callGraphStatistics: CallGraphStatistics<JcMethod>,
    ): UPathSelector<JcState>
}
