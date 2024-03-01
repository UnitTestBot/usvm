package org.usvm.python.ps

enum class PyPathSelectorType {
    BaselinePriorityDfs,  // passes tests
    BaselineWeightedDfs,  // passes tests
    BaselinePriorityNumberOfVirtualDfs,  // passes tests
    BaselinePriorityPlusTypeRatingByHintsDfs,  // passes tests
    DelayedForkByInstructionPriorityDfs,  // fails tests
    DelayedForkByInstructionWeightedDfs,  // passes tests
    DelayedForkByInstructionWeightedRandomTree,  // passes tests
    DelayedForkByInstructionPriorityNumberOfVirtualDfs,  // passes tests
    DelayedForkByInstructionWeightedNumberOfVirtualDfs,  // fails test MultiplyAndCompare
}