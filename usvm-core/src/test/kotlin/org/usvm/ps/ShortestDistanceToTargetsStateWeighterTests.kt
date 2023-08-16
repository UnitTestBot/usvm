package org.usvm.ps

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.usvm.TestState
import org.usvm.UCallStack
import kotlin.test.assertEquals

@OptIn(ExperimentalUnsignedTypes::class)
internal class ShortestDistanceToTargetsStateWeighterTests {

    private val methodAShortestDistanceMatrix = arrayOf(
        uintArrayOf(),
        uintArrayOf(5u, 0u, 1u, 2u),
        uintArrayOf(UInt.MAX_VALUE, UInt.MAX_VALUE, 0u, 4u),
        uintArrayOf(3u, UInt.MAX_VALUE, 1u, 0u)
    )

    private val methodBShortestDistanceMatrix = arrayOf(
        uintArrayOf(),
        uintArrayOf(15u, 0u, UInt.MAX_VALUE, 4u, 12u, 3u, 1u),
        uintArrayOf(2u, UInt.MAX_VALUE, 0u, 1u, 1u, 9u, 3u),
        uintArrayOf(UInt.MAX_VALUE, 5u, UInt.MAX_VALUE, 0u, 15u, 4u, 1u),
        uintArrayOf(2u, UInt.MAX_VALUE, UInt.MAX_VALUE, UInt.MAX_VALUE, 0u, 2u, 2u),
        uintArrayOf(UInt.MAX_VALUE, UInt.MAX_VALUE, 8u, UInt.MAX_VALUE, UInt.MAX_VALUE, 0u, 1u),
        uintArrayOf(UInt.MAX_VALUE, UInt.MAX_VALUE, UInt.MAX_VALUE, UInt.MAX_VALUE, UInt.MAX_VALUE, 1u, 0u)
    )

    private val methodCShortestDistanceMatrix = arrayOf(
        uintArrayOf(),
        uintArrayOf(4u, 0u, 9u, 1u, 4u),
        uintArrayOf(2u, UInt.MAX_VALUE, 0u, 5u, 20u),
        uintArrayOf(4u, 3u, UInt.MAX_VALUE, 0u, 10u),
        uintArrayOf(3u, 12u, 8u, UInt.MAX_VALUE, 0u)
    )

    private val shortestDistances = mapOf(
        "A" to methodAShortestDistanceMatrix,
        "B" to methodBShortestDistanceMatrix,
        "C" to methodCShortestDistanceMatrix
    )

    @Test
    fun smokeTest() {
        fun getCfgDistance(method: String, from: Int, to: Int): UInt {
            require(method == "A")
            require(from == 1)
            return when (to) {
                2 -> 1u
                3 -> 2u
                4 -> 1u
                5 -> 3u
                6 -> 7u
                else -> UInt.MAX_VALUE
            }
        }

        val weighter = ShortestDistanceToTargetsStateWeighter<_, _, TestState>(
            setOf("A" to 2, "A" to 3, "A" to 4, "A" to 5, "A" to 6),
            ::getCfgDistance
        ) { _, _ -> 1u }

        val mockState = mockk<TestState>()
        every { mockState.currentStatement } returns 1
        val callStack = UCallStack<String, Int>("A")
        every { mockState.callStack } returns callStack

        assertEquals(1u, weighter.weight(mockState))
        weighter.removeTarget("A", 2)
        assertEquals(1u, weighter.weight(mockState))
        weighter.removeTarget("A", 4)
        assertEquals(2u, weighter.weight(mockState))
        weighter.removeTarget("A", 3)
        assertEquals(3u, weighter.weight(mockState))
        weighter.removeTarget("A", 5)
        assertEquals(7u, weighter.weight(mockState))
        weighter.removeTarget("A", 6)
        assertEquals(UInt.MAX_VALUE, weighter.weight(mockState))
    }

    @Test
    fun multipleFrameDistanceTest() {
        fun getCfgDistance(method: String, from: Int, to: Int): UInt {
            return shortestDistances.getValue(method)[from][to]
        }

        fun getCfgDistanceToExitPoint(method: String, from: Int): UInt {
            return shortestDistances.getValue(method)[from][0]
        }

        val mockState = mockk<TestState>()
        val callStack = UCallStack<String, Int>("A")
        callStack.push("B", 3)
        callStack.push("C", 2)
        every { mockState.currentStatement } returns 3
        every { mockState.callStack } returns callStack

        val weighter =
            ShortestDistanceToTargetsStateWeighter<_, _, TestState>(setOf("C" to 4), ::getCfgDistance, ::getCfgDistanceToExitPoint)
        assertEquals(10u, weighter.weight(mockState))

        weighter.removeTarget("C", 4)
        weighter.addTarget("A", 2)
        assertEquals(7u, weighter.weight(mockState))

        weighter.addTarget("C", 4)
        assertEquals(7u, weighter.weight(mockState))

        weighter.addTarget("B", 3)
        assertEquals(5u, weighter.weight(mockState))

        weighter.addTarget("C", 1)
        assertEquals(3u, weighter.weight(mockState))

        callStack.pop()
        every { mockState.currentStatement } returns 5
        assertEquals(UInt.MAX_VALUE, weighter.weight(mockState))

        callStack.pop()
        every { mockState.currentStatement } returns 2
        assertEquals(0u, weighter.weight(mockState))

        weighter.removeTarget("A", 2)
        assertEquals(UInt.MAX_VALUE, weighter.weight(mockState))

        callStack.push("C", 1)
        callStack.push("C", 1)
        every { mockState.currentStatement } returns 2
        assertEquals(2u, weighter.weight(mockState))
    }

}
