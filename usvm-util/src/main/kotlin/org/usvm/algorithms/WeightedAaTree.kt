package org.usvm.algorithms

import java.util.Stack
import kotlin.math.min

data class AaTreeNode<T>(
    /**
     * Value attached to the node.
     */
    val value: T,
    /**
     * Weight attached to the node.
     */
    val weight: Double,
    /**
     * Sum of the children's weights.
     */
    val weightSum: Double,
    /**
     * Node's depth in the tree. 1 for root node, 2 for its children etc.
     */
    val level: Int,
    /**
     * Reference to the left child.
     */
    val left: AaTreeNode<T>?,
    /**
     * Reference to the right child.
     */
    val right: AaTreeNode<T>?
)

/**
 * Balanced AA-tree of [AaTreeNode].
 *
 * @param comparator comparator to arrange elements in the tree. Doesn't affect the element's weights.
 */
class WeightedAaTree<T>(private val comparator: Comparator<T>) {

    var count: Int = 0
        private set

    var root: AaTreeNode<T>? = null
        private set

    private fun AaTreeNode<T>.update(
        value: T = this.value,
        weight: Double = this.weight,
        level: Int = this.level,
        left: AaTreeNode<T>? = this.left,
        right: AaTreeNode<T>? = this.right
    ): AaTreeNode<T> {
        val leftWeightSum = left?.weightSum ?: 0.0
        val rightWeightSum = right?.weightSum ?: 0.0
        return AaTreeNode(value = value, weight = weight, weightSum = leftWeightSum + rightWeightSum + weight, level = level, left = left, right = right)
    }

    private fun AaTreeNode<T>.skew(): AaTreeNode<T> {
        if (left?.level == level) {
            return left.update(right = update(left = left.right))
        }

        return this
    }

    private fun AaTreeNode<T>.split(): AaTreeNode<T> {
        if (level == right?.right?.level) {
            return right.update(level = right.level + 1, left = update(right = right.left))
        }

        return this
    }

    private tailrec fun addRec(value: T, weight: Double, insertTo: AaTreeNode<T>?, k: (AaTreeNode<T>) -> AaTreeNode<T>): AaTreeNode<T> {
        if (insertTo == null) {
            count++
            return k(AaTreeNode(value, weight, weight, 1, null, null))
        }

        val compareResult = comparator.compare(value, insertTo.value)

        return when {
            compareResult < 0 ->
                addRec(value, weight, insertTo.left) { k(insertTo.update(left = it).skew().split()) }
            compareResult > 0 ->
                addRec(value, weight, insertTo.right) { k(insertTo.update(right = it).skew().split()) }
            else -> insertTo
        }
    }

    private fun AaTreeNode<T>.decreaseLevel(): AaTreeNode<T> {
        val shouldBe = min(left?.level ?: 0, right?.level ?: 0) + 1
        if (shouldBe >= level) {
            return this
        }

        val updatedRight =
            if (shouldBe >= (right?.level ?: 0)) {
                right
            } else {
                checkNotNull(right)
                right.update(level = shouldBe)
            }

        return update(level = shouldBe, right = updatedRight)
    }

    private fun AaTreeNode<T>.succ(): AaTreeNode<T>? {
        var node = this.right
        while (node?.left != null) {
            node = node.left
        }
        return node
    }

    private fun AaTreeNode<T>.pred(): AaTreeNode<T>? {
        var node = this.left
        while (node?.right != null) {
            node = node.right
        }
        return node
    }

    private fun AaTreeNode<T>.balanceAfterRemove(): AaTreeNode<T> {
        return decreaseLevel().skew().run {
            val skewedRight = right?.skew()
            val skewedRightRight = skewedRight?.right?.skew()

            update(right = skewedRight?.update(right = skewedRightRight))
                .split()
                .run { update(right = right?.split()) }
        }
    }

    private tailrec fun removeRec(value: T, deleteFrom: AaTreeNode<T>?, k: (AaTreeNode<T>?) -> AaTreeNode<T>?): AaTreeNode<T>? {
        if (deleteFrom == null) {
            return null
        }

        val compareResult = comparator.compare(value, deleteFrom.value)

        if (compareResult < 0) {
            return removeRec(value, deleteFrom.left) { k(deleteFrom.update(left = it).balanceAfterRemove()) }
        }

        if (compareResult > 0) {
            return removeRec(value, deleteFrom.right) { k(deleteFrom.update(right = it).balanceAfterRemove()) }
        }

        if (deleteFrom.right == null && deleteFrom.level == 1) {
            count--
            return k(null)
        }

        if (deleteFrom.left == null) {
            val succ = deleteFrom.succ()
            checkNotNull(succ)
            return removeRec(succ.value, deleteFrom.right) { k(deleteFrom.update(value = succ.value, right = it).balanceAfterRemove()) }
        }

        val pred = deleteFrom.pred()
        checkNotNull(pred)
        return removeRec(pred.value, deleteFrom.left) { k(deleteFrom.update(value = pred.value, left = it).balanceAfterRemove()) }
    }

    /**
     * Adds an element with specified weight.
     *
     * @return True if the element didn't exist in the tree and was added.
     */
    fun add(value: T, weight: Double): Boolean {
        val prevCount = count
        root = addRec(value, weight, root) { it }
        return prevCount != count
    }

    /**
     * Removes an element.
     *
     * @return True if the element existed in the tree and was removed.
     */
    fun remove(value: T): Boolean {
        val prevCount = count
        root = removeRec(value, root) { it }
        return prevCount != count
    }

    /**
     * Returns the sequence of nodes in pre-order manner.
     */
    fun preOrderTraversal(): Sequence<AaTreeNode<T>> {
        val currentRoot = root ?: return emptySequence()
        var node: AaTreeNode<T>
        val stack = Stack<AaTreeNode<T>>()
        stack.push(currentRoot)
        return sequence {
            while (stack.isNotEmpty()) {
                node = stack.pop()
                yield(node)
                if (node.right != null) {
                    stack.push(node.right)
                }
                if (node.left != null) {
                    stack.push(node.left)
                }
            }
        }
    }
}
