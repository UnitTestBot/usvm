package org.usvm.test

import org.junit.jupiter.api.Test
import org.usvm.util.AaTreeNode
import org.usvm.util.WeightedAaTree
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

internal class WeightedAaTreeTests {

    private fun checkInvariants(node : AaTreeNode<*>) {
        // The level of every leaf node is one
        if (node.left == null && node.right == null) {
            assertEquals(1, node.level)
        }

        // The level of every left child is exactly one less than that of its parent
        if (node.left != null) {
            assertEquals(node.level - 1, node.left!!.level)
        }

        // The level of every right child is equal to or one less than that of its parent
        if (node.right != null) {
            assertTrue(node.right!!.level == node.level - 1 || node.right!!.level == node.level)

            // The level of every right grandchild is strictly less than that of its grandparent
            if (node.right!!.right != null) {
                assertTrue(node.right!!.right!!.level < node.level)
            }
        }

        // Every node of level greater than one has two children
        if (node.level > 1) {
            assertNotNull(node.left)
            assertNotNull(node.right)
        }

        // Additional weighted tree invariant
        assertEquals(node.weight, node.weightSum - (node.left?.weightSum ?: 0f) - (node.right?.weightSum ?: 0f), 1e-5f)
    }

    @Test
    fun simpleCountTest() {
        val tree = WeightedAaTree<Int>(naturalOrder())
        tree.add(5, 5f)
        assertEquals(1, tree.count)
    }

    @Test
    fun sameElementsAreNotAddedTest() {
        val tree = WeightedAaTree<Int>(naturalOrder())
        tree.add(5, 5f)
        tree.add(5, 10f)
        assertEquals(1, tree.count)
    }

    @Test
    fun preOrderTraversalTraversesAllNodesTest() {
        val tree = WeightedAaTree<Int>(naturalOrder())
        val elementsCount = 1000

        for (i in 1..elementsCount) {
            val value = pseudoRandom(i)
            val weight = i.toFloat()
            tree.add(value, weight)
        }

        val traversed = HashSet<Int>()
        fun traverse(node : AaTreeNode<Int>) {
            if (traversed.contains(node.value)) {
                fail()
            }
            traversed.add(node.value)
        }

        tree.preOrderTraversal().forEach(::traverse)
        assertEquals(elementsCount, traversed.size)
    }

    @Test
    fun treeInvariantsAreSatisfiedTest() {
        val tree = WeightedAaTree<Int>(naturalOrder())
        val elementsCount = 1000

        for (i in 1..elementsCount) {
            val value = pseudoRandom(i)
            val weight = i.toFloat()
            tree.add(value, weight)
        }

        assertEquals(elementsCount, tree.count)
        tree.preOrderTraversal().forEach(::checkInvariants)

        for (i in 1..elementsCount) {
            val value = pseudoRandom(i)
            tree.remove(value)
            tree.preOrderTraversal().forEach(::checkInvariants)
            assertEquals(elementsCount - i, tree.count)
        }
    }
}
