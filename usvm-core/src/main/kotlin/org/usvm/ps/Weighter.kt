package org.usvm.ps

fun interface Weighter<in State, out Weight> {
    fun weight(state: State): Weight
}
