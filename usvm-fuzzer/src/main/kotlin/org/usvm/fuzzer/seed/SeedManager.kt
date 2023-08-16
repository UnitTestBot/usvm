package org.usvm.fuzzer.seed

import org.usvm.fuzzer.strategy.ChoosingStrategy

class SeedManager(
    initialSeeds: List<Seed>,
    private val seedsLimit: Int,
    private val seedSelectionStrategy: ChoosingStrategy<Seed>
) {

    val seeds = initialSeeds.toMutableList()

    fun addSeed(seed: Seed, iterationNumber: Int) {
        if (seeds.size >= seedsLimit) {
            seeds.add(seed)
            val worstSeed = seedSelectionStrategy.chooseWorst(seeds, iterationNumber)
            seeds.remove(worstSeed)
        } else {
            seeds.add(seed)
        }
    }

    fun getSeed(iterationNumber: Int) = seedSelectionStrategy.chooseBest(seeds, iterationNumber)

}