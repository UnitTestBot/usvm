import RandomTreePathSelectorTests.TreeBuilder.Companion.tree
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.usvm.PathNode
import org.usvm.TestInstruction
import org.usvm.TestState
import org.usvm.ps.ExecutionTreeTracker
import org.usvm.ps.RandomTreePathSelector
import org.usvm.pseudoRandom
import kotlin.test.assertEquals

internal class RandomTreePathSelectorTests {
    private class TreeBuilder(
        val node: PathNode<TestInstruction>,
    ) {
        fun child(init: TreeBuilder.() -> Unit) {
            val nextNode = node + nextStatement()
            val builder = TreeBuilder(nextNode)
            init(builder)
        }

        fun child(state: TestState) {
            with(state) {
                pathNode = node + nextStatement()
            }
        }

        companion object {
            // We pass the same state everywhere
            private val staticState = mockk<TestState>()
            private var counter = 0

            fun nextStatement() = TestInstruction("", counter++)

            fun tree(init: TreeBuilder.() -> Unit): PathNode<TestInstruction> {
                val rootNode = PathNode.root<TestInstruction>()
                val builder = TreeBuilder(rootNode)
                init(builder)
                return rootNode
            }
        }
    }

    @Test
    fun smokeTest() {
        val root = PathNode.root<TestInstruction>()
        val state1 = mockk<TestState>()

        every { state1.pathNode } returns root

        val selector = RandomTreePathSelector.fromRoot<TestState, TestInstruction>(root) { 0 }

        selector.add(listOf(state1))
        assertEquals(state1, selector.peek())
    }

    @Test
    fun peekFromEmptySelectorAndNonEmptyPathsTreeTest() {
        val root = PathNode.root<TestInstruction>()
        val state1 = mockk<TestState>()

        every { state1.pathNode } returns root

        val selector = RandomTreePathSelector.fromRoot<TestState, TestInstruction>(root) { 0 }

        assertThrows<NoSuchElementException> { selector.peek() }
    }

    @ParameterizedTest
    @MethodSource("testCases")
    fun regularPeekTest(
        root: PathNode<TestInstruction>,
        states: List<TestState>,
        randomChoices: List<Int>,
        expectedStates: List<TestState>,
    ) {
        var currentRandomIdx = -1

        fun nextInt(max: Int): Int {
            currentRandomIdx++
            return randomChoices[currentRandomIdx] % max
        }

        val selector =
            RandomTreePathSelector<TestState, TestInstruction>(ExecutionTreeTracker(root) { it.pathNode }, ::nextInt)
        selector.add(states)

        for (expectedState in expectedStates) {
            assertEquals(expectedState, selector.peek())
        }
    }

    @Test
    fun removeTest() {
        val states = (0 until 15).map {
            val mock = mockk<TestState>()

            every { mock.pathNode } answers { callOriginal() }
            every { mock.pathNode = any() } answers { callOriginal() }

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

        fun nextInt(max: Int): Int {
            currentRandomIdx++
            return pseudoRandom(currentRandomIdx) % max
        }

        val selector =
            RandomTreePathSelector<TestState, TestInstruction>(ExecutionTreeTracker(root) { it.pathNode }, ::nextInt)

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

        @JvmStatic
        fun testCases(): Collection<Arguments> {
            val states = (0 until 20).map {
                val mock = mockk<TestState>()

                every { mock.pathNode } answers { callOriginal() }
                every { mock.pathNode = any() } answers { callOriginal() }

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
                            child(states[4])
                        }
                    }
                }
            }

            val root3 = tree {
                child(states[5])

                child {
                    child {
                        child(states[6])

                        child {
                            child {
                                child(states[10])
                                child(states[11])
                                child(states[12])
                            }
                            child {
                                child(states[13])
                                child(states[14])
                            }
                        }

                        child {
                            child {
                                child(states[15])
                                child(states[16])
                            }
                            child {
                                child(states[17])
                                child(states[18])
                                child(states[19])
                            }
                        }
                    }
                    child(states[7])
                }

                child {
                    child(states[8])
                    child(states[9])
                }
            }

            return listOf(
                Arguments.of(root1, states.slice(0 until 4), listOf(1, 1), listOf(states[3])),
                Arguments.of(root1, states.slice(0 until 4), listOf(0, 0, 1, 0), listOf(states[0], states[2])),
                Arguments.of(root1, states.slice(0 until 3), listOf(1, 1, 1, 1), listOf(states[2])),
                Arguments.of(root1, states.slice(0 until 2), listOf(1, 1, 1, 1, 1, 1), listOf(states[1])),
                Arguments.of(root2, states.slice(4 until 5), listOf(100, 200, 4, 55), listOf(states[4])),
                Arguments.of(root3, states.slice(5 until 20), listOf(1, 0, 1, 0, 0), listOf(states[10])),
                Arguments.of(root3, states.slice(5 until 20), listOf(1, 0, 1, 0, 2), listOf(states[12])),
                Arguments.of(root3, states.slice(5 until 20), listOf(2, 1), listOf(states[9])),
                Arguments.of(root3, states.slice(5 until 20), listOf(0), listOf(states[5]))
            )
        }
    }
}
