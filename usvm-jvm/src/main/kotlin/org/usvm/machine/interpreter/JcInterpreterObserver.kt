package org.usvm.machine.interpreter

import org.usvm.UHeapRef
import org.usvm.machine.state.JcState
import org.usvm.statistics.UMachineObserver

interface JcInterpreterObserver {
    fun onNullPointerDereference(state: JcState, ref: UHeapRef) { }
}

class CompositeJcInterpreterObserver(observers: List<JcInterpreterObserver>) : JcInterpreterObserver {
    private val observers = observers.toMutableList()

    constructor(vararg observers: JcInterpreterObserver) : this(observers.toMutableList())

    override fun onNullPointerDereference(state: JcState, ref: UHeapRef) {
        observers.forEach { it.onNullPointerDereference(state, ref) }
    }

    operator fun plusAssign(observer: JcInterpreterObserver) {
        observers.add(observer)
    }

    operator fun minusAssign(observer: JcInterpreterObserver) {
        observers.remove(observer)
    }
}
