package org.usvm.fuzzer.mutation

import org.usvm.fuzzer.generator.DataFactory
import org.usvm.fuzzer.seed.Seed
import org.usvm.fuzzer.strategy.Selectable

abstract class Mutation : Selectable() {
    protected lateinit var dataFactory: DataFactory

    fun appendSeedFactory(dataFactory: DataFactory): Mutation =
        also { this.dataFactory = dataFactory }

    protected abstract val mutationFun: DataFactory.(Seed) -> Pair<Seed?, MutationInfo>?

    fun mutate(seed: Seed): Pair<Seed?, MutationInfo>? =
        mutationFun.invoke(dataFactory, seed)

}