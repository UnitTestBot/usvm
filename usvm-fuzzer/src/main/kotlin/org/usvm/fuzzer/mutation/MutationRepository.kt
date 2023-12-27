package org.usvm.fuzzer.mutation

import org.jacodb.api.JcClasspath
import org.usvm.fuzzer.generator.GeneratorContext
import org.usvm.fuzzer.generator.GeneratorRepository
import org.usvm.fuzzer.generator.SeedFactory
import org.usvm.fuzzer.strategy.ChoosingStrategy

class MutationRepository(
    private val strategy: ChoosingStrategy<Mutation>,
    private val seedFactory: SeedFactory
) {

    private val mutations = mutableListOf<Mutation>(
//        AddPrimitiveConstant(),
//        CallRandomMethod()
    )

    fun getMutation(iterationNumber: Int): Mutation =
        strategy.chooseBest(mutations, iterationNumber).appendSeedFactory(seedFactory)

}