package org.usvm.statistics

import org.usvm.UState
import java.util.concurrent.ConcurrentHashMap

class StatisticsObservable<Method, Statement, State : UState<*, *, Method, Statement>> : StatisticsObserver<Method, Statement, State> {
    private val observers: MutableSet<StatisticsObserver<Method, Statement, State>> = ConcurrentHashMap.newKeySet()

    operator fun plusAssign(observer: StatisticsObserver<Method, Statement, State>) {
        observers.add(observer)
    }

    operator fun minusAssign(observer: StatisticsObserver<Method, Statement, State>) {
        observers.remove(observer)
    }

    override fun onStatementCovered(method: Method, statement: Statement) {
        observers.forEach { it.onStatementCovered(method, statement) }
    }

    override fun onStateTerminated(state: State) {
        observers.forEach { it.onStateTerminated(state) }
    }

    override fun onStateForked(parent: State, forks: Collection<State>) {
        observers.forEach { it.onStateForked(parent, forks) }
    }

    override fun onStep(state: State) {
        observers.forEach { it.onStep(state) }
    }
}
