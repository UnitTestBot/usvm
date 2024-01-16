package org.usvm.fuzzer.mutation

import org.usvm.fuzzer.generator.DataFactory
import org.usvm.fuzzer.strategy.ChoosingStrategy

class MutationRepository(
    private val strategy: ChoosingStrategy<Mutation>,
    private val dataFactory: DataFactory
) {

    private val mutations = mutableListOf<Mutation>(
//        AddPrimitiveConstant(),
//        CallRandomMethod()
    )

    fun getMutation(iterationNumber: Int): Mutation =
        strategy.chooseBest(mutations, iterationNumber).appendSeedFactory(dataFactory)

}