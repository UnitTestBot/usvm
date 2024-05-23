package org.usvm.python.ps

enum class PyPathSelectorType {
    BaselinePriorityDfs, // passes tests
    BaselineWeightedDfs, // passes tests
    BaselinePriorityNumberOfVirtualDfs, // passes tests
    BaselineWeightedNumberOfVirtualRandomTree, // passes tests
    BaselinePriorityNumberOfInstructionsDfs, // passes tests
    BaselinePriorityNumberOfInstructionsRandomTree, // passes tests
    BaselinePriorityPlusTypeRatingByHintsDfs, // passes tests
    DelayedForkByInstructionWeightedDfs, // passes tests
    DelayedForkByInstructionWeightedRandomTree, // passes tests
    DelayedForkByInstructionPriorityNumberOfVirtualDfs, // passes tests
    DelayedForkByInstructionWeightedNumberOfVirtualRandomTree, // passes tests
    DelayedForkByInstructionPriorityNumberOfInstructionsDfs, // passes tests
    DelayedForkByInstructionPriorityNumberOfInstructionsRandomTree, // passes tests
    DelayedForkByInstructionPriorityNumberOfInstructionsRandomTreePlusTypeRating, // passes tests
}
