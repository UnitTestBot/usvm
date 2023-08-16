package org.usvm.fuzzer.strategy

interface ChoosingStrategy<T: Selectable> {

    fun chooseBest(from: List<T>, iterationNumber: Int): T
    fun chooseWorst(from: List<T>, iterationNumber: Int): T

}