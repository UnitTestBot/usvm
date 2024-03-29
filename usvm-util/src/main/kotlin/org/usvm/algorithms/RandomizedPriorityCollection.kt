package org.usvm.algorithms

/**
 * [UPriorityCollection] implementation which peeks elements randomly with distribution based on priority.
 *
 * Implemented with tree set in which each node contains sum of its children weights (priorities).
 *
 * To peek an element, a random point in interval [[0..sum of all leaf weights]] is selected, then the node which
 * weight interval contains this point is found in binary-search manner.
 *
 * @param comparator comparator for elements to arrange them in tree. It doesn't affect the priorities.
 * @param unitIntervalRandom function returning a random value in [[0..1]] interval which is used to peek the element.
 */
class RandomizedPriorityCollection<T>(comparator: Comparator<T>, private val unitIntervalRandom: () -> Double) :
    UPriorityCollection<T, Double> {

    private val tree = WeightedAaTree(comparator)

    override val count: Int get() = tree.count

    private tailrec fun peekRec(targetPoint: Double, leftEndpoint: Double, peekAt: AaTreeNode<T>): T {
        val leftSegmentEndsAt = leftEndpoint + (peekAt.left?.weightSum ?: 0.0)
        val currentSegmentEndsAt = leftSegmentEndsAt + peekAt.weight
        return when {
            targetPoint < leftSegmentEndsAt && peekAt.left != null ->
                peekRec(targetPoint, leftEndpoint, peekAt.left)
            targetPoint > currentSegmentEndsAt && peekAt.right != null ->
                peekRec(targetPoint, currentSegmentEndsAt, peekAt.right)
            else -> peekAt.value
        }
    }

    override fun peek(): T {
        val root = tree.root ?: throw NoSuchElementException("Discrete PDF was empty")
        val randomValue = unitIntervalRandom()
        check(randomValue in 0.0..1.0) { "Random generator in discrete PDF returned a number outside the unit interval (${randomValue})" }
        val randomValueScaled = randomValue * root.weightSum
        return peekRec(randomValueScaled, 0.0, root)
    }

    override fun update(element: T, priority: Double) {
        remove(element)
        add(element, priority)
    }

    override fun remove(element: T) {
        if (!tree.remove(element)) {
            throw NoSuchElementException("Element not found in discrete PDF")
        }
    }

    override fun add(element: T, priority: Double) {
        require(priority >= 0.0) { "Discrete PDF cannot store elements with negative priority" }
        check(tree.add(element, priority)) { "Element already exists in discrete PDF" }
    }
}
