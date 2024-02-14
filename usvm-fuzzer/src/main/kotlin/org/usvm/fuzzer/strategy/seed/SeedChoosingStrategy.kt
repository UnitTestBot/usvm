package org.usvm.fuzzer.strategy.seed

import org.usvm.fuzzer.seed.Seed

interface SeedChoosingStrategy {

    fun chooseWorst(): Seed
    fun chooseBest(): Seed
    fun chooseWorstAndRemove(): Seed
    fun chooseWorstAndRemove(recentlyAddedSeed: Seed): Seed
}