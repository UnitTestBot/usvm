package org.usvm.ts.pbt.registry

/** Service-loaded source of one named Kotlin property registry for the command-line runner. */
interface PropertyRegistryProvider {
    /** Stable CLI identifier used to select this registry. */
    val registryId: String

    /** Builds the registry after the provider has been selected. */
    fun load(): PropertyRegistry
}
