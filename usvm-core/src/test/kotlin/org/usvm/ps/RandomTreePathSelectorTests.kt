package org.usvm.ps

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.usvm.UState
import org.usvm.hash
import org.usvm.statistics.PathsTreeNode
import org.usvm.statistics.PathsTreeStatistics
import kotlin.test.assertEquals
import kotlin.test.assertTrue

typealias State = UState<*, *, Any, Any>

internal class RandomTreePathSelectorTests {

    class PathsTreeNodeMock<T>(
        override val children: MutableList<PathsTreeNode<T>>
    ) : PathsTreeNode<T> {
        override val depth: Int = 0

        override var state: T? = null

        override var parent: PathsTreeNode<T>? = null

        override val ignoreTokens = mutableSetOf<Int>()
        override fun addIgnoreToken(token: Int) {
            ignoreTokens.add(token)
        }
    }

    private class TreeBuilder {
        val node = PathsTreeNodeMock<State>(mutableListOf())

        fun child(init: TreeBuilder.() -> Unit) {
            val builder = TreeBuilder()
            init(builder)
            builder.node.parent = node
            node.children.add(builder.node)
        }

        fun child(state: State) {
            val leafNode = PathsTreeNodeMock<State>(mutableListOf())
            leafNode.parent = node
            leafNode.state = state
            node.children.add(leafNode)
        }
    }

    @Test
    fun smokeTest() {
        val statistics = mockk<PathsTreeStatistics<Any, Any, State>>()
        val state1 = mockk<State>()
        val root = tree { child(state1) }
        every { statistics.root } returns root
        val selector = RandomTreePathSelector(statistics, 0) { 0 }
        selector.add(listOf(state1))
        assertEquals(state1, selector.peek())
    }

    @Test
    fun peekFromEmptySelectorAndNonEmptyStatisticsTest() {
        val statistics = mockk<PathsTreeStatistics<Any, Any, State>>()
        val state1 = mockk<State>()
        val root = tree { child(state1) }
        every { statistics.root } returns root
        val selector = RandomTreePathSelector(statistics, 0) { 0 }
        assertThrows<NoSuchElementException> { selector.peek() }
    }

    @Test
    fun peekStateNotContainedInStatisticsTest() {
        val statistics = mockk<PathsTreeStatistics<Any, Any, State>>()
        val state1 = mockk<State>()
        val root = tree { child(state1) }
        every { statistics.root } returns root
        val state2 = mockk<State>()
        val selector = RandomTreePathSelector(statistics, 0) { 0 }
        selector.add(listOf(state2))
        assertEquals(state2, selector.peek())
        selector.remove(state2)
        assertThrows<NoSuchElementException> { selector.peek() }
    }

    @ParameterizedTest
    @MethodSource("testCases")
    fun regularPeekTest(root: PathsTreeNodeMock<State>, states: List<State>, randomChoices: List<Int>, expectedStates: List<State>) {
        val statistics = mockk<PathsTreeStatistics<Any, Any, State>>()
        every { statistics.root } returns root
        var currentRandomIdx = -1
        val selector = RandomTreePathSelector(statistics, 0) {
            currentRandomIdx++
            randomChoices[currentRandomIdx]
        }
        selector.add(states)
        for (expectedState in expectedStates) {
            assertEquals(expectedState, selector.peek())
        }
    }

    @Test
    fun removeTest() {
        val states = (0 until 15).map { mockk<State>() }.toTypedArray()
        val statistics = mockk<PathsTreeStatistics<Any, Any, State>>()
        val root = tree {
            child(states[0])

            child {
                child {
                    child(states[2])

                    child {
                        child {
                            child(states[5])
                            child(states[6])
                            child(states[7])
                        }
                        child {
                            child(states[8])
                            child(states[9])
                        }
                    }

                    child {
                        child {
                            child(states[10])
                            child(states[11])
                        }
                        child {
                            child(states[12])
                            child(states[13])
                            child(states[14])
                        }
                    }
                }
                child(states[1])
            }

            child {
                child(states[3])
                child(states[4])
            }
        }
        every { statistics.root } returns root
        var currentRandomIdx = -1
        val selector = RandomTreePathSelector(statistics, 0) {
            currentRandomIdx++
            hash(currentRandomIdx)
        }
        val currentStates = states.toMutableSet()
        selector.add(currentStates)
        for (state in states) {
            for (i in 0 until 10) {
                assertTrue(selector.peek() in currentStates)
            }
            selector.remove(state)
            currentStates.remove(state)
        }
    }

    companion object {
        private fun tree(init: TreeBuilder.() -> Unit): PathsTreeNodeMock<State> {
            val builder = TreeBuilder()
            init(builder)
            return builder.node
        }

        @JvmStatic
        fun testCases(): Collection<Arguments> {
            val states = (0 until 15).map { mockk<State>() }.toTypedArray()

            val root1 = tree {
                child {
                    child(states[0])
                    child(states[1])
                }

                child {
                    child(states[2])
                    child(states[3])
                }
            }

            val root2 = tree {
                child {
                    child {
                        child {
                            child(states[0])
                        }
                    }
                }
            }

            val root3 = tree {
                child(states[0])

                child {
                    child {
                        child(states[2])

                        child {
                            child {
                                child(states[5])
                                child(states[6])
                                child(states[7])
                            }
                            child {
                                child(states[8])
                                child(states[9])
                            }
                        }

                        child {
                            child {
                                child(states[10])
                                child(states[11])
                            }
                            child {
                                child(states[12])
                                child(states[13])
                                child(states[14])
                            }
                        }
                    }
                    child(states[1])
                }

                child {
                    child(states[3])
                    child(states[4])
                }
            }

            return listOf(
                Arguments.of(root1, states.slice(0 until 4), listOf(1, 1), listOf(states[3])),
                Arguments.of(root1, states.slice(0 until 4), listOf(0, 0, 1, 0), listOf(states[0], states[2])),
                Arguments.of(root1, states.slice(0 until 3), listOf(1, 1, 1, 1), listOf(states[2])),
                Arguments.of(root1, states.slice(0 until 2), listOf(1, 1, 1, 1, 1, 1), listOf(states[1])),
                Arguments.of(root2, states.slice(0 until 1), listOf(100, 200, 4, 55), listOf(states[0])),
                Arguments.of(root3, states.toList(), listOf(1, 0, 1, 0, 0), listOf(states[5])),
                Arguments.of(root3, states.toList(), listOf(1, 0, 1, 0, 2), listOf(states[7])),
                Arguments.of(root3, states.toList(), listOf(2, 1), listOf(states[4])),
                Arguments.of(root3, states.toList(), listOf(0), listOf(states[0]))
            )
        }
    }
}
