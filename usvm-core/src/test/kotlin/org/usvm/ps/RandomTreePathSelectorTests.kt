package org.usvm.ps

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.usvm.TestState
import org.usvm.UState
import org.usvm.pseudoRandom
import org.usvm.PathsTrieNode
import org.usvm.PathsTrieNodeImpl
import org.usvm.RootNode
import kotlin.test.assertEquals

internal class RandomTreePathSelectorTests {
    private class TreeBuilder(
        prevNode: PathsTrieNode<TestState, Int>,
        statement: Int,
    ) {
        val node = when (prevNode) {
            is RootNode -> PathsTrieNodeImpl(prevNode, statement, staticState)
            is PathsTrieNodeImpl -> PathsTrieNodeImpl(prevNode, statement, staticState)
        }

        fun child(init: TreeBuilder.() -> Unit) {
            val builder = TreeBuilder(node, nextStatement())
            init(builder)
            node.children[builder.node.statement] = builder.node
        }

        fun child(state: TestState) {
            val stmt = nextStatement()

            with(state) {
                pathLocation = node.pathLocationFor(stmt, this)
            }
        }

        companion object {
            // We pass the same state everywhere
            private val staticState = mockk<TestState>()
            private var counter = 0

            fun nextStatement() = counter++
        }
    }

    @Test
    fun smokeTest() {
        val rootNode = RootNode<TestState, Int>()
        val state1 = mockk<TestState>()

        every { state1.pathLocation } returns PathsTrieNodeImpl(rootNode, statement = 1, state1)

        val selector = RandomTreePathSelector(rootNode, { 0 }, 0L)

        selector.add(listOf(state1))
        assertEquals(state1, selector.peek())
    }

    @Test
    fun peekFromEmptySelectorAndNonEmptyPathsTreeTest() {
        val rootNode = RootNode<TestState, Int>()
        val state1 = mockk<TestState>()

        every { state1.pathLocation } returns PathsTrieNodeImpl(rootNode, statement = 1, state1)

        val selector = RandomTreePathSelector(rootNode, { 0 }, 0L)

        assertThrows<NoSuchElementException> { selector.peek() }
    }

    @ParameterizedTest
    @MethodSource("testCases")
    fun regularPeekTest(
        root: PathsTrieNode<TestState, Int>,
        states: List<TestState>,
        randomChoices: List<Int>,
        expectedStates: List<TestState>,
    ) {
        var currentRandomIdx = -1

        fun nextInt(): Int {
            currentRandomIdx++
            return randomChoices[currentRandomIdx]
        }

        val selector = RandomTreePathSelector(root, ::nextInt, 0L)
        registerLocationsInTree(root, selector)
        selector.add(states)

        for (expectedState in expectedStates) {
            assertEquals(expectedState, selector.peek())
        }
    }

    @Test
    fun removeTest() {
        val states = (0 until 15).map {
            val mock = mockk<TestState>()

            every { mock.pathLocation } answers { callOriginal() }
            every { mock.pathLocation = any() } answers { callOriginal() }

            mock
        }.toTypedArray()

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

        var currentRandomIdx = -1

        fun nextInt(): Int {
            currentRandomIdx++
            return pseudoRandom(currentRandomIdx)
        }

        val selector = RandomTreePathSelector(root, ::nextInt, 0L)

        registerLocationsInTree(root, selector)

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
        private fun <State : UState<*, *, *, Statement, *, State>, Statement> registerLocationsInTree(
            root: PathsTrieNode<State, Statement>,
            selector: RandomTreePathSelector<State, Statement>,
        ) {
            selector.registerLocation(root)

            root.children.forEach {
                registerLocationsInTree(it.value, selector)
            }
        }

        private fun tree(init: TreeBuilder.() -> Unit): PathsTrieNode<TestState, Int> {
            val rootNode = RootNode<TestState, Int>()
            val builder = TreeBuilder(rootNode, statement = TreeBuilder.nextStatement())
            init(builder)
            return rootNode
        }

        @JvmStatic
        fun testCases(): Collection<Arguments> {
            val states = (0 until 15).map {
                val mock = mockk<TestState>()

                every { mock.pathLocation } answers { callOriginal() }
                every { mock.pathLocation = any() } answers { callOriginal() }

                mock
            }.toTypedArray()

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
                Arguments.of(root1, states.slice(0 until 4), listOf(0, 1, 1), listOf(states[3])),
                Arguments.of(root1, states.slice(0 until 4), listOf(0, 0, 0, 0, 1, 0), listOf(states[0], states[2])),
                Arguments.of(root1, states.slice(0 until 3), listOf(0, 1, 1, 1, 1), listOf(states[2])),
                Arguments.of(root1, states.slice(0 until 2), listOf(0, 1, 1, 1, 1, 1, 1), listOf(states[1])),
                Arguments.of(root2, states.slice(0 until 1), listOf(0, 100, 200, 4, 55), listOf(states[0])),
                Arguments.of(root3, states.toList(), listOf(0, 1, 0, 1, 0, 0), listOf(states[5])),
                Arguments.of(root3, states.toList(), listOf(0, 1, 0, 1, 0, 2), listOf(states[7])),
                Arguments.of(root3, states.toList(), listOf(0, 2, 1), listOf(states[4])),
                Arguments.of(root3, states.toList(), listOf(0, 0), listOf(states[0]))
            )
        }
    }
}
