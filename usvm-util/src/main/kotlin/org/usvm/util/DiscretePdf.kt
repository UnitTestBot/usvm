package org.usvm.util

class DiscretePdf<T>(comparator: Comparator<T>, private val unitIntervalRandom: () -> Float) : UPriorityCollection<T, Float> {

    private val tree = WeightedAaTree(comparator)

    override val count: Int get() = tree.count

    private tailrec fun peekRec(targetPoint: Float, leftEndpoint: Float, peekAt: AaTreeNode<T>): T {
        val leftSegmentEndsAt = leftEndpoint + (peekAt.left?.weightSum ?: 0f)
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
        check(randomValue in 0f..1f) { "Random generator in discrete PDF returned a number outside the unit interval (${randomValue})" }
        val randomValueScaled = randomValue * root.weightSum
        return peekRec(randomValueScaled, 0f, root)
    }

    override fun update(element: T, priority: Float) {
        remove(element)
        add(element, priority)
    }

    override fun remove(element: T) {
        if (!tree.remove(element)) {
            throw NoSuchElementException("Element not found in discrete PDF")
        }
    }

    override fun add(element: T, priority: Float) {
        require(priority >= 0f) { "Discrete PDF cannot store elements with negative priority" }
        check(tree.add(element, priority)) { "Element already exists in discrete PDF" }
    }
}
