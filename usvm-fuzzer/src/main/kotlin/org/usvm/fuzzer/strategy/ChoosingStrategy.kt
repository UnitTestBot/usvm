package org.usvm.fuzzer.strategy

interface ChoosingStrategy<T: Selectable> {

    fun chooseBest(from: Collection<T>, iterationNumber: Int): T
    fun chooseWorst(from: Collection<T>, iterationNumber: Int): T

}