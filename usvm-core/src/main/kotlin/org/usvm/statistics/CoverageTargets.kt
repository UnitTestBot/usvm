package org.usvm.statistics

/**
 * Search goals with stable equality, read and updated on the analysis thread.
 * A changed goal set must have a new [revision].
 */
interface CoverageTargets<Target> {
    val uncoveredTargets: Collection<Target>
    val revision: Long
}

/**
 * Caller-owned coverage goals, updated on the analysis thread between machine steps.
 * Reporting coverage does not remove queued states or establish a property on other inputs.
 */
class ExternalCoverageTargets<Target>(targets: Collection<Target>) : CoverageTargets<Target> {
    private val knownTargets = targets.toMutableSet()
    private val pendingTargets = targets.toMutableSet()

    override val uncoveredTargets: Set<Target>
        get() = pendingTargets.toSet()

    override var revision: Long = 0
        private set

    /** Registers newly identified goals; previously covered goals stay covered. */
    fun addTargets(targets: Collection<Target>): Boolean {
        val additions = targets.filter { knownTargets.add(it) }
        if (additions.isEmpty()) return false

        pendingTargets.addAll(additions)
        revision++
        return true
    }

    /** Accepts caller-confirmed coverage of known goals. Unknown goals are ignored. */
    fun markCovered(targets: Collection<Target>): Boolean {
        val changed = pendingTargets.removeAll(targets.toSet())
        if (changed) revision++

        return changed
    }
}
