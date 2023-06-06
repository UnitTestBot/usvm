package org.usvm.statistics

import org.usvm.UState
import java.util.concurrent.ConcurrentHashMap

class StatisticsObservable<Method, Statement> : StatisticsObserver<Method, Statement> {
    private val observers: MutableSet<StatisticsObserver<Method, Statement>> = ConcurrentHashMap.newKeySet()

    operator fun plusAssign(observer: StatisticsObserver<Method, Statement>) {
        observers.add(observer)
    }

    operator fun minusAssign(observer: StatisticsObserver<Method, Statement>) {
        observers.remove(observer)
    }

    override fun onStatementCovered(method: Method, statement: Statement) {
        observers.forEach { it.onStatementCovered(method, statement) }
    }

    override fun onStateTerminated(state: UState<*, *, Method, Statement>) {
        observers.forEach { it.onStateTerminated(state) }
    }

    override fun onStep(state: UState<*, *, Method, Statement>) {
        observers.forEach { it.onStep(state) }
    }
}