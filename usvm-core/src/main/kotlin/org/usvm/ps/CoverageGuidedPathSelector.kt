package org.usvm.ps

import org.usvm.UPathSelector
import org.usvm.algorithms.DeterministicPriorityCollection
import org.usvm.statistics.CoverageTargets

/**
 * Selects the closest uncovered goal without pruning states. Goals may be statements, edges or other caller types.
 * [distance] owns their semantics; [UInt.MAX_VALUE] means unreachable. Updates become visible on the next peek.
 */
fun <State, Target> createCoverageGuidedPathSelector(
    coverageTargets: CoverageTargets<Target>,
    distance: (State, Target) -> UInt,
): UPathSelector<State> {
    var revision = coverageTargets.revision
    var targets = coverageTargets.uncoveredTargets.toList()

    return WeightedPathSelector(
        priorityCollectionFactory = { DeterministicPriorityCollection(Comparator.naturalOrder<UInt>()) },
        weighter = { state ->
            if (revision != coverageTargets.revision) {
                targets = coverageTargets.uncoveredTargets.toList()
                revision = coverageTargets.revision
            }

            targets.minOfOrNull { target -> distance(state, target) } ?: UInt.MAX_VALUE
        },
        weightRevision = { coverageTargets.revision },
    )
}
