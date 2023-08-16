package org.usvm.fuzzer.mutation

import org.usvm.fuzzer.strategy.ChoosingStrategy
import java.util.concurrent.atomic.AtomicInteger

class MutationManager(
    val mutations: List<Mutation>,
    private val strategy: ChoosingStrategy<Mutation>
) {

    fun getMutation(iterationNumber: Int): Mutation = strategy.chooseBest(mutations, iterationNumber)

}