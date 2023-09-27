package org.usvm.forkblacklists

import org.usvm.UState
import org.usvm.statistics.distances.MultiTargetDistanceCalculator
import org.usvm.targets.UTarget

/**
 * [UForkBlackList] implementation which disallows forks to locations from which no targets are reachable.
 */
class TargetsReachableForkBlackList<State, Target, Method, Statement, Distance>(
    private val distanceCalculator: MultiTargetDistanceCalculator<Method, Statement, Distance>,
    private val shouldBlackList: Distance.() -> Boolean,
) : UForkBlackList<State, Statement> where State : UState<*, Method, Statement, *, Target, State>,
                                           Target : UTarget<Statement, Target> {

    override fun shouldForkTo(state: State, stmt: Statement): Boolean {
        return state.targets.any { target ->
            val targetLocation = target.location ?: return@any true
            !distanceCalculator.calculateDistance(stmt, state.callStack, targetLocation).shouldBlackList()
        }
    }
}
