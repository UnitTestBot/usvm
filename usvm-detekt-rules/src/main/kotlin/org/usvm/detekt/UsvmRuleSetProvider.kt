package org.usvm.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

/** Makes USVM-specific rules discoverable by Detekt. */
class UsvmRuleSetProvider : RuleSetProvider {
    override val ruleSetId: String = "usvm"

    override fun instance(config: Config): RuleSet = RuleSet(
        id = ruleSetId,
        rules = listOf(
            MultilineWhenBranchBraces(config),
            NestedCallArguments(config),
        ),
    )
}
