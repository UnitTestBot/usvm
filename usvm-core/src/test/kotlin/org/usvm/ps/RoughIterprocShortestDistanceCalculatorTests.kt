package org.usvm.ps

import io.mockk.every
import org.junit.jupiter.api.Test
import org.usvm.UCallStack
import kotlin.test.assertEquals

@OptIn(ExperimentalUnsignedTypes::class)
internal class RoughIterprocShortestDistanceCalculatorTests {

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

        val calculator = RoughIterprocShortestDistanceCalculator(
            setOf("A" to 2, "A" to 3, "A" to 4, "A" to 5, "A" to 6),
            ::getCfgDistance
        ) { _, _ -> 1u }

        val currentStatement = 1
        val callStack = UCallStack<String, Int>("A")

        assertEquals(1u, calculator.calculateDistance(currentStatement, callStack))
        calculator.removeTarget("A", 2)
        assertEquals(1u, calculator.calculateDistance(currentStatement, callStack))
        calculator.removeTarget("A", 4)
        assertEquals(2u, calculator.calculateDistance(currentStatement, callStack))
        calculator.removeTarget("A", 3)
        assertEquals(3u, calculator.calculateDistance(currentStatement, callStack))
        calculator.removeTarget("A", 5)
        assertEquals(7u, calculator.calculateDistance(currentStatement, callStack))
        calculator.removeTarget("A", 6)
        assertEquals(UInt.MAX_VALUE, calculator.calculateDistance(currentStatement, callStack))
    }

    @Test
    fun multipleFrameDistanceTest() {
        fun getCfgDistance(method: String, from: Int, to: Int): UInt {
            return shortestDistances.getValue(method)[from][to]
        }

        fun getCfgDistanceToExitPoint(method: String, from: Int): UInt {
            return shortestDistances.getValue(method)[from][0]
        }

        var currentStatment = 3
        val callStack = UCallStack<String, Int>("A")
        callStack.push("B", 3)
        callStack.push("C", 2)

        val calculator =
            RoughIterprocShortestDistanceCalculator(setOf("C" to 4), ::getCfgDistance, ::getCfgDistanceToExitPoint)
        assertEquals(10u, calculator.calculateDistance(currentStatment, callStack))

        calculator.removeTarget("C", 4)
        calculator.addTarget("A", 2)
        assertEquals(7u, calculator.calculateDistance(currentStatment, callStack))

        calculator.addTarget("C", 4)
        assertEquals(7u, calculator.calculateDistance(currentStatment, callStack))

        calculator.addTarget("B", 3)
        assertEquals(5u, calculator.calculateDistance(currentStatment, callStack))

        calculator.addTarget("C", 1)
        assertEquals(3u, calculator.calculateDistance(currentStatment, callStack))

        callStack.pop()
        currentStatment = 5
        assertEquals(UInt.MAX_VALUE, calculator.calculateDistance(currentStatment, callStack))

        callStack.pop()
        currentStatment = 2
        assertEquals(0u, calculator.calculateDistance(currentStatment, callStack))

        calculator.removeTarget("A", 2)
        assertEquals(UInt.MAX_VALUE, calculator.calculateDistance(currentStatment, callStack))

        callStack.push("C", 1)
        callStack.push("C", 1)
        currentStatment = 2
        assertEquals(2u, calculator.calculateDistance(currentStatment, callStack))
    }

}
