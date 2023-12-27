package org.usvm.fuzzer.mutation

import org.usvm.fuzzer.generator.SeedFactory
import org.usvm.fuzzer.seed.Seed
import org.usvm.fuzzer.strategy.Selectable
import org.usvm.fuzzer.util.UTestValueRepresentation

abstract class Mutation : Selectable() {
    protected lateinit var seedFactory: SeedFactory

    fun appendSeedFactory(seedFactory: SeedFactory): Mutation =
        also { this.seedFactory = seedFactory }

    protected abstract val mutationFun: SeedFactory.(Seed) -> Seed?

    fun mutate(seed: Seed): Seed? =
        mutationFun.invoke(seedFactory, seed)

}