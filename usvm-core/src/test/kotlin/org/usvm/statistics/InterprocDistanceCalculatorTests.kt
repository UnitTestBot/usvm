package org.usvm.statistics

import kotlin.test.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.usvm.TestInstruction
import org.usvm.UCallStack
import org.usvm.appGraph
import org.usvm.callStackOf
import org.usvm.statistics.distances.CallGraphStatisticsImpl
import org.usvm.statistics.distances.CfgStatisticsImpl
import org.usvm.statistics.distances.InterprocDistance
import org.usvm.statistics.distances.InterprocDistanceCalculator
import org.usvm.statistics.distances.PlainCallGraphStatistics
import org.usvm.statistics.distances.ReachabilityKind

class InterprocDistanceCalculatorTests {

    private val appGraph1 = appGraph {
        method("A", 8) {
            entryPoint(0)
            edge(0, 1)
            edge(0, 2)
            edge(1, 7)
            edge(7, 3)
            edge(2, 4)
            edge(4, 5)
            edge(5, 6)
            edge(6, 3)
            exitPoint(3)
        }

        method("B", 22) {
            entryPoint(0)
            edge(0, 1)
            edge(1, 2)
            call(2, "H")
            edge(2, 3)
            edge(3, 4)
            call(4, "D")
            edge(4, 5)
            edge(5, 6)
            edge(6, 7)

            edge(7, 8)
            edge(7, 9)
            call(8, "C")
            call(9, "C")

            edge(2, 10)
            edge(10, 11)
            edge(11, 12)
            edge(12, 10)
            edge(12, 6)
            edge(6, 18)

            edge(2, 13)
            edge(13, 14)
            edge(14, 15)
            call(14, "E")
            call(15, "E")
            edge(14, 16)
            edge(15, 17)
            edge(16, 17)
            call(17, "B")
            edge(17, 6)
            edge(16, 18)
            edge(18, 19)
            call(19, "F")

            edge(2, 20)
            edge(20, 21)
            call(21, "C")

            exitPoint(8)
            exitPoint(9)
            exitPoint(19)
            exitPoint(21)
        }

        method("C", 2) {
            entryPoint(0)
            edge(0, 1)
            exitPoint(1)
        }

        method("D", 9) {
            entryPoint(0)

            edge(0, 2)
            edge(2, 4)
            edge(2, 5)
            call(5, "F")
            edge(5, 6)
            edge(6, 4)
            edge(6, 8)
            exitPoint(8)

            edge(0, 1)
            exitPoint(1)

            edge(2, 3)
            exitPoint(3)

            edge(6, 7)
            call(7, "C")
            exitPoint(7)
        }

        method("E", 4) {
            entryPoint(0)
            edge(0, 1)
            edge(1, 2)
            call(1, "F")
            exitPoint(2)
            edge(1, 3)
            exitPoint(3)
        }

        method("F", 6) {
            entryPoint(0)
            edge(0, 1)
            call(1, "G")
            edge(1, 2)
            call(2, "G")
            edge(2, 3)
            call(3, "G")
            edge(3, 4)
            call(4, "G")
            edge(4, 5)
            exitPoint(5)
        }

        method("G", 8) {
            entryPoint(0)
            edge(0, 1)
            edge(1, 2)
            edge(1, 3)
            edge(1, 4)
            edge(2, 5)
            edge(3, 5)
            edge(4, 5)
            edge(4, 7)
            edge(5, 6)
            exitPoint(6)
            exitPoint(7)
        }

        method("H", 2) {
            entryPoint(0)
            edge(0, 1)
            exitPoint(1)
            call(1, "I")
        }

        method("I", 1) {
            entryPoint(0)
            exitPoint(0)
            call(0, "E")
        }
    }

    @ParameterizedTest
    @MethodSource("testCases")
    fun `Interprocedural distance calculator test`(
        callGraphReachabilityDepth: Int,
        callStack: UCallStack<String, TestInstruction>,
        fromLoc: TestInstruction,
        targetLoc: TestInstruction,
        expectedDist: InterprocDistance
    ) {
        val cfgStatistics = CfgStatisticsImpl(appGraph1)
        val callGraphStatistics =
            when (callGraphReachabilityDepth) {
                0 -> PlainCallGraphStatistics()
                else -> CallGraphStatisticsImpl(callGraphReachabilityDepth.toUInt(), appGraph1)
            }

        val calculator = InterprocDistanceCalculator(
            targetLoc.method to targetLoc,
            appGraph1,
            cfgStatistics,
            callGraphStatistics
        )
        assertEquals(expectedDist, calculator.calculateDistance(fromLoc, callStack))
    }

    companion object {
        @JvmStatic
        fun testCases(): Collection<Arguments> {
            return listOf(
                Arguments.of(
                    0,
                    callStackOf("B"),
                    TestInstruction("B", 2),
                    TestInstruction("B", 18),
                    InterprocDistance(4u, ReachabilityKind.LOCAL)
                ),
                Arguments.of(
                    0,
                    callStackOf("B"),
                    TestInstruction("B", 2),
                    TestInstruction("D", 1),
                    InterprocDistance(2u, ReachabilityKind.UP_STACK)
                ),
                Arguments.of(
                    0,
                    callStackOf("B", "D" to 4, "F" to 5, "G" to 3),
                    TestInstruction("G", 4),
                    TestInstruction("B", 18),
                    InterprocDistance(1u, ReachabilityKind.DOWN_STACK)
                ),
                Arguments.of(
                    0,
                    callStackOf("B", "D" to 4, "F" to 5, "G" to 3),
                    TestInstruction("G", 4),
                    TestInstruction("B", 11),
                    InterprocDistance(UInt.MAX_VALUE, ReachabilityKind.NONE)
                ),
                Arguments.of(
                    0,
                    callStackOf("B"),
                    TestInstruction("B", 2),
                    TestInstruction("E", 1),
                    InterprocDistance(2u, ReachabilityKind.UP_STACK)
                ),
                Arguments.of(
                    0,
                    callStackOf("D", "F" to 5, "G" to 3),
                    TestInstruction("G", 0),
                    TestInstruction("D", 4),
                    InterprocDistance(3u, ReachabilityKind.DOWN_STACK)
                ),
                Arguments.of(
                    0,
                    callStackOf("B", "F" to 19, "G" to 2),
                    TestInstruction("G", 5),
                    TestInstruction("G", 1),
                    InterprocDistance(1u, ReachabilityKind.DOWN_STACK)
                ),
                Arguments.of(
                    0,
                    callStackOf("B"),
                    TestInstruction("B", 13),
                    TestInstruction("B", 1),
                    InterprocDistance(3u, ReachabilityKind.UP_STACK)
                ),
                Arguments.of(
                    0,
                    callStackOf("B"),
                    TestInstruction("B", 17),
                    TestInstruction("B", 17),
                    InterprocDistance(0u, ReachabilityKind.LOCAL)
                ),
                Arguments.of(
                    0,
                    callStackOf("B"),
                    TestInstruction("B", 0),
                    TestInstruction("C", 1),
                    InterprocDistance(4u, ReachabilityKind.UP_STACK)
                ),
                // This target can be achieved only with call graph BFS depth > 0
                Arguments.of(
                    0,
                    callStackOf("B"),
                    TestInstruction("B", 0),
                    TestInstruction("G", 1),
                    InterprocDistance(UInt.MAX_VALUE, ReachabilityKind.NONE)
                ),
                // Going through B-19
                Arguments.of(
                    1,
                    callStackOf("B"),
                    TestInstruction("B", 0),
                    TestInstruction("G", 1),
                    InterprocDistance(7u, ReachabilityKind.UP_STACK)
                ),
                // Going through B-4
                Arguments.of(
                    2,
                    callStackOf("B"),
                    TestInstruction("B", 0),
                    TestInstruction("G", 1),
                    InterprocDistance(4u, ReachabilityKind.UP_STACK)
                ),
                // Going through B-4
                Arguments.of(
                    3,
                    callStackOf("B"),
                    TestInstruction("B", 0),
                    TestInstruction("G", 1),
                    InterprocDistance(4u, ReachabilityKind.UP_STACK)
                ),
                // Going through B-2
                Arguments.of(
                    4,
                    callStackOf("B"),
                    TestInstruction("B", 0),
                    TestInstruction("G", 1),
                    InterprocDistance(2u, ReachabilityKind.UP_STACK)
                ),
                Arguments.of(
                    0,
                    callStackOf("B", "E" to 14, "F" to 1, "G" to 2),
                    TestInstruction("G", 3),
                    TestInstruction("I", 0),
                    InterprocDistance(UInt.MAX_VALUE, ReachabilityKind.NONE)
                ),
                Arguments.of(
                    1,
                    callStackOf("B", "E" to 14, "F" to 1, "G" to 2),
                    TestInstruction("G", 3),
                    TestInstruction("I", 0),
                    InterprocDistance(UInt.MAX_VALUE, ReachabilityKind.NONE)
                ),
                // Going recursively to B through B-17, then through B-2
                Arguments.of(
                    2,
                    callStackOf("B", "E" to 14, "F" to 1, "G" to 2),
                    TestInstruction("G", 3),
                    TestInstruction("I", 0),
                    InterprocDistance(2u, ReachabilityKind.DOWN_STACK)
                ),
                Arguments.of(
                    0,
                    callStackOf("B", "E" to 15, "F" to 1, "G" to 4),
                    TestInstruction("G", 3),
                    TestInstruction("E", 1),
                    InterprocDistance(UInt.MAX_VALUE, ReachabilityKind.NONE)
                ),
                // Going recursively to B through B-17, then through B-14
                Arguments.of(
                    1,
                    callStackOf("B", "E" to 15, "F" to 1, "G" to 4),
                    TestInstruction("G", 3),
                    TestInstruction("E", 1),
                    InterprocDistance(2u, ReachabilityKind.DOWN_STACK)
                ),
            )
        }
    }
}
