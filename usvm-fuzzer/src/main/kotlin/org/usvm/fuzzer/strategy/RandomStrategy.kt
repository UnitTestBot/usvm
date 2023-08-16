package org.usvm.fuzzer.strategy

class RandomStrategy<T: Selectable>: ChoosingStrategy<T> {
    override fun chooseBest(from: List<T>, iterationNumber: Int) = from.random()

    override fun chooseWorst(from: List<T>, iterationNumber: Int) = from.random()
}