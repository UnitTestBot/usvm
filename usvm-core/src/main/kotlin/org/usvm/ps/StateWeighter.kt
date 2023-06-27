package org.usvm.ps

/**
 * @see weight
 */
fun interface StateWeighter<in State, out Weight> {

    /**
     * Returns state's weight for [WeightedPathSelector].
     */
    fun weight(state: State): Weight
}
