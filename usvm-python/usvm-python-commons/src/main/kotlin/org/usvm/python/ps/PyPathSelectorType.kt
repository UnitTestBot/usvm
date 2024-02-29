package org.usvm.python.ps

enum class PyPathSelectorType {
    BaselinePriorityDfs,
    BaselineWeightedDfs,
    BaselinePriorityRandomTree,
    BaselinePriorityWeightedByNumberOfVirtual,
    BaselinePriorityPlusTypeRatingByHintsDfs,
    DelayedForkByInstructionPriorityDfs,
    DelayedForkByInstructionWeightedDfs,
    DelayedForkByInstructionWeightedRandomTree
}