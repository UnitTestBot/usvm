package org.usvm.fuzzer.strategy

class RandomStrategy<T: Selectable>: ChoosingStrategy<T> {
    override fun chooseBest(from: Collection<T>, iterationNumber: Int) = from.random()

    override fun chooseWorst(from: Collection<T>, iterationNumber: Int) = from.random()
}