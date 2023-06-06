package org.usvm.ps

fun interface StateWeighter<in State, out Weight> {
    fun weight(state: State): Weight
}
