package org.usvm.util

class DiscretePdf<T>(private val unitIntervalRandom: () -> Float, comparator: Comparator<T>) : UPriorityCollection<T, Float> {

    private val tree = WeightedAaTree(comparator)

    override val count: Int get() = tree.count

    private tailrec fun peekRec(targetPoint: Float, leftEndpoint: Float, peekAt: AaTreeNode<T>): T {
        val leftSegmentEndsAt = leftEndpoint + (peekAt.left?.weight ?: 0f)
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
        val randomValueScaled = unitIntervalRandom() * root.weight
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
        check(tree.add(element, priority)) { "Element already exists in discrete PDF" }
    }
}
