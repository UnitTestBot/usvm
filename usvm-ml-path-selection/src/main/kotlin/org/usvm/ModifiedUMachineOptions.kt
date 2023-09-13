package org.usvm

enum class ModifiedPathSelectionStrategy {
    /**
     * Collects features according to states selected by any other path selector.
     */
    FEATURES_LOGGING,
    /**
     * Collects features and feeds them to the ML model to select states.
     * Extends FEATURE_LOGGING path selector.
     */
    MACHINE_LEARNING,
}

data class ModifiedUMachineOptions(
    val basicOptions: UMachineOptions = UMachineOptions(),
    val pathSelectionStrategies: List<ModifiedPathSelectionStrategy> =
        listOf(ModifiedPathSelectionStrategy.MACHINE_LEARNING)
)
