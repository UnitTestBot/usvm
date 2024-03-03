package org.usvm.python.ps

enum class PyPathSelectorType {
    BaselinePriorityDfs,  // passes tests
    BaselineWeightedDfs,  // passes tests
    BaselinePriorityNumberOfVirtualDfs,  // passes tests
    BaselinePriorityNumberOfInstructionsDfs,  // passes tests
    BaselinePriorityPlusTypeRatingByHintsDfs,  // passes tests
    DelayedForkByInstructionPriorityDfs,  // fails tests
    DelayedForkByInstructionWeightedDfs,  // passes tests
    DelayedForkByInstructionWeightedRandomTree,  // passes tests
    DelayedForkByInstructionPriorityNumberOfVirtualDfs,  // passes tests
    DelayedForkByInstructionWeightedNumberOfVirtualDfs,  // fails test MultiplyAndCompare
    DelayedForkByInstructionPriorityNumberOfInstructionsDfs,  // passes tests
    DelayedForkByInstructionWeightedNumberOfInstructionsDfs,  // fails tests ReverseUsage and MultiplyAndCompare
}