package org.usvm.ts.pbt.cli

import org.usvm.ts.pbt.model.IntegerDomain
import org.usvm.ts.pbt.model.PropertyDefinition
import org.usvm.ts.pbt.model.PropertyId
import org.usvm.ts.pbt.model.PropertyInput
import org.usvm.ts.pbt.model.TypeScriptEntryPoint
import org.usvm.ts.pbt.registry.PropertyRegistry
import org.usvm.ts.pbt.registry.PropertyRegistryProvider

class InstalledDistributionRegistryProvider : PropertyRegistryProvider {
    override val registryId: String = "distribution-fixture"

    override fun load(): PropertyRegistry = PropertyRegistry(
        listOf(
            PropertyDefinition(
                id = PropertyId("distribution.always-true"),
                inputs = listOf(
                    PropertyInput(
                        name = "value",
                        domain = IntegerDomain(min = -10, max = 10),
                    ),
                ),
                predicate = TypeScriptEntryPoint(
                    module = "properties/execution/ExecutionProperties.ts",
                    exportName = "alwaysTrue",
                ),
            ),
            PropertyDefinition(
                id = PropertyId("distribution.covers-positive"),
                inputs = listOf(
                    PropertyInput(
                        name = "value",
                        domain = IntegerDomain(min = 1, max = 10),
                    ),
                ),
                predicate = TypeScriptEntryPoint(
                    module = "properties/coverage/CoverageProperties.ts",
                    exportName = "coversPositive",
                ),
            ),
        ),
    )
}
