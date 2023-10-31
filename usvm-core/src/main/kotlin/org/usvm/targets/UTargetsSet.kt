package org.usvm.targets

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList

class UTargetsSet<Target, Statement> private constructor(
    private var targets: PersistentList<Target>,
) : Sequence<Target>
    where Target : UTarget<Statement, Target> {
    private val reachedTerminalTargetsImpl = mutableSetOf<Target>()

    /**
     * Reached targets with no children.
     */
    val reachedTerminal: Set<Target> = reachedTerminalTargetsImpl

    /**
     * If the [target] is not removed and is contained in this state's target collection,
     * removes it from there and adds there all its children.
     *
     * @return true if the [target] was successfully removed.
     */
    internal fun tryPropagateTarget(target: Target): Boolean {
        val previousTargetCount = targets.size
        targets = targets.remove(target)

        if (previousTargetCount == targets.size || target.isRemoved) {
            return false
        }

        if (target.isTerminal) {
            reachedTerminalTargetsImpl.add(target)
            return true
        }

        targets = targets.addAll(target.children)

        return true
    }

    /**
     * Collection of state's current targets.
     * TODO: clean removed targets sometimes
     */
    override fun iterator(): Iterator<Target> = targets.asSequence().filterNot { it.isRemoved }.iterator()

    fun clone(): UTargetsSet<Target, Statement> = UTargetsSet(targets)

    companion object {
        fun <Target : UTarget<Statement, Target>, Statement> empty() = UTargetsSet<Target, Statement>(persistentListOf())
        fun <Target : UTarget<Statement, Target>, Statement> from(targets: List<Target>) =
            UTargetsSet(targets.toPersistentList())
    }
}
