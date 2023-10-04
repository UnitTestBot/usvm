package org.usvm

enum class MLPathSelectionStrategy {
    /**
     * Collects features according to states selected by any other path selector.
     */
    FEATURES_LOGGING,

    /**
     * Collects features and feeds them to the ML model to select states.
     * Extends FEATURE_LOGGING path selector.
     */
    MACHINE_LEARNING,

    /**
     * Selects states with best Graph Neural Network state score
     */
    GNN,
}

data class MLMachineOptions(
    val basicOptions: UMachineOptions,
    val pathSelectionStrategy: MLPathSelectionStrategy,
    val heteroGNNModelPath: String = ""
)
