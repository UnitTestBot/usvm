package org.usvm.util

import java.util.*
import kotlin.math.min

data class AaTreeNode<T>(
    val value: T,
    val weight: Float,
    val weightSum: Float,
    val level: Int,
    val left: AaTreeNode<T>?,
    val right: AaTreeNode<T>?
)

class WeightedAaTree<T>(private val comparator: Comparator<T>) {

    var count: Int = 0
        private set

    var root: AaTreeNode<T>? = null
        private set

    private fun AaTreeNode<T>.update(
        value: T = this.value,
        weight: Float = this.weight,
        level: Int = this.level,
        left: AaTreeNode<T>? = this.left,
        right: AaTreeNode<T>? = this.right
    ): AaTreeNode<T> {
        val leftWeightSum = left?.weightSum ?: 0f
        val rightWeightSum = right?.weightSum ?: 0f
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

    private tailrec fun addRec(value: T, weight: Float, insertTo: AaTreeNode<T>?, k: (AaTreeNode<T>) -> AaTreeNode<T>): AaTreeNode<T> {
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

        return when {
            compareResult < 0 ->
                removeRec(value, deleteFrom.left) { k(deleteFrom.update(left = it).balanceAfterRemove()) }
            compareResult > 0 ->
                removeRec(value, deleteFrom.right) { k(deleteFrom.update(right = it).balanceAfterRemove()) }
            else -> {
                when {
                    deleteFrom.right == null && deleteFrom.level == 1 -> {
                        count--
                        k(null)
                    }
                    deleteFrom.left == null -> {
                        val succ = deleteFrom.succ()
                        checkNotNull(succ)
                        removeRec(succ.value, deleteFrom.right) { k(deleteFrom.update(value = succ.value, right = it).balanceAfterRemove()) }
                    }
                    else -> {
                        val pred = deleteFrom.pred()
                        checkNotNull(pred)
                        removeRec(pred.value, deleteFrom.left) { k(deleteFrom.update(value = pred.value, left = it).balanceAfterRemove()) }
                    }
                }
            }
        }
    }

    fun add(value: T, weight: Float): Boolean {
        val prevCount = count
        root = addRec(value, weight, root) { it }
        return prevCount != count
    }

    fun remove(value: T): Boolean {
        val prevCount = count
        root = removeRec(value, root) { it }
        return prevCount != count
    }

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
