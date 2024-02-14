package org.usvm.fuzzer.mutation

import org.usvm.fuzzer.generator.DataFactory
import org.usvm.fuzzer.mutation.`object`.CallRandomMethod
import org.usvm.fuzzer.mutation.`object`.RandomValueRegenerator
import org.usvm.fuzzer.mutation.primitives.FieldFiller
import org.usvm.fuzzer.strategy.ChoosingStrategy

class MutationRepository(
    private val strategy: ChoosingStrategy<Mutation>,
    private val dataFactory: DataFactory
) {

    private val mutations = mutableListOf<Mutation>(
        RandomValueRegenerator(),
        FieldFiller(),
        CallRandomMethod()
    )

    fun getMutation(iterationNumber: Int): Mutation =
        strategy.chooseBest(mutations, iterationNumber).appendSeedFactory(dataFactory)

    fun printStats() {
        println("-------------------------------------")
        mutations.forEach { mutation ->
            println("Mutation: ${mutation::class.java.name}")
            println("Score: ${mutation.score}")
            println("Number of picks: ${mutation.numberOfChooses}")
            println("--------------")
        }
        println("-------------------------------------")
    }

}