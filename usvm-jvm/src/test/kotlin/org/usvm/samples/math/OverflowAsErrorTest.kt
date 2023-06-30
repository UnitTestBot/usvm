// TODO unsupported

//package org.usvm.samples.math
//
//import org.junit.jupiter.api.Disabled
//import org.junit.jupiter.api.Test
//import org.usvm.samples.JavaMethodTestRunner
//import org.usvm.samples.algorithms.Sort
//
//import org.usvm.test.util.checkers.eq
//import kotlin.math.floor
//import kotlin.math.sqrt
//
//internal class OverflowAsErrorTest : JavaMethodTestRunner() {
//    @Test
//    fun testIntOverflow() {
//            checkWithExceptionExecutionMatches(
//                OverflowExamples::intOverflow,
//                eq(5),
//                { _, x, _, r ->
//                    val overflowOccurred = kotlin.runCatching {
//                        Math.multiplyExact(x, x)
//                    }.isFailure
//                    overflowOccurred && r.isException<OverflowDetectionError>()
//                }, // through overflow
//                { _, x, _, r ->
//                    val twoMul = Math.multiplyExact(x, x)
//                    val overflowOccurred = kotlin.runCatching {
//                        Math.multiplyExact(twoMul, x)
//                    }.isFailure
//                    overflowOccurred && r.isException<OverflowDetectionError>()
//                }, // through overflow (2nd '*')
//                { _, x, _, r -> x * x * x >= 0 && x >= 0 && r.getOrNull() == 0 },
//                { _, x, y, r -> x * x * x > 0 && x > 0 && y == 10 && r.getOrNull() == 1 },
//                { _, x, y, r -> x * x * x > 0 && x > 0 && y != 10 && r.getOrNull() == 0 },
//            )
//        }
//    }
//
//    @Test
//    fun testByteAddOverflow() {
//        withTreatingOverflowAsError {
//            checkWithExceptionExecutionMatches(
//                OverflowExamples::byteAddOverflow,
//                eq(2),
//                { _, _, r -> r != null && !r.isException<OverflowDetectionError>() },
//                { _, x, y, r ->
//                    val negOverflow = ((x + y).toByte() >= 0 && x < 0 && y < 0)
//                    val posOverflow = ((x + y).toByte() <= 0 && x > 0 && y > 0)
//                    (negOverflow || posOverflow) && r.isException<OverflowDetectionError>()
//                }, // through overflow
//            )
//        }
//    }
//
//    @Test
//    fun testByteWithIntOverflow() {
//        withTreatingOverflowAsError {
//            checkWithExceptionExecutionMatches(
//                OverflowExamples::byteWithIntOverflow,
//                eq(2),
//                { _, x, y, r ->
//                    runCatching {
//                        Math.addExact(x.toInt(), y)
//                    }.isFailure && r.isException<OverflowDetectionError>()
//                },
//                { _, x, y, r -> Math.addExact(x.toInt(), y).toByte() == r.getOrThrow() }
//            )
//        }
//    }
//
//    @Test
//    fun testByteSubOverflow() {
//        withTreatingOverflowAsError {
//            checkWithExceptionExecutionMatches(
//                OverflowExamples::byteSubOverflow,
//                eq(2),
//                { _, _, r -> r != null && !r.isException<OverflowDetectionError>() },
//                { _, x, y, r ->
//                    val negOverflow = ((x - y).toByte() >= 0 && x < 0 && y > 0)
//                    val posOverflow = ((x - y).toByte() <= 0 && x > 0 && y < 0)
//                    (negOverflow || posOverflow) && r.isException<OverflowDetectionError>()
//                }, // through overflow
//            )
//        }
//    }
//
//    @Test
//    fun testByteMulOverflow() {
//        withTreatingOverflowAsError {
//            checkWithExceptionExecutionMatches(
//                OverflowExamples::byteMulOverflow,
//                eq(2),
//                { _, _, r -> r != null && !r.isException<OverflowDetectionError>() },
//                { _, _, r -> r.isException<OverflowDetectionError>() }, // through overflow
//            )
//        }
//    }
//
//    @Test
//    fun testShortAddOverflow() {
//        withTreatingOverflowAsError {
//            checkWithExceptionExecutionMatches(
//                OverflowExamples::shortAddOverflow,
//                eq(2),
//                { _, _, r -> r != null && !r.isException<OverflowDetectionError>() },
//                { _, x, y, r ->
//                    val negOverflow = ((x + y).toShort() >= 0 && x < 0 && y < 0)
//                    val posOverflow = ((x + y).toShort() <= 0 && x > 0 && y > 0)
//                    (negOverflow || posOverflow) && r.isException<OverflowDetectionError>()
//                }, // through overflow
//            )
//        }
//    }
//
//    @Test
//    fun testShortSubOverflow() {
//        withTreatingOverflowAsError {
//            checkWithExceptionExecutionMatches(
//                OverflowExamples::shortSubOverflow,
//                eq(2),
//                { _, _, r -> r != null && !r.isException<OverflowDetectionError>() },
//                { _, x, y, r ->
//                    val negOverflow = ((x - y).toShort() >= 0 && x < 0 && y > 0)
//                    val posOverflow = ((x - y).toShort() <= 0 && x > 0 && y < 0)
//                    (negOverflow || posOverflow) && r.isException<OverflowDetectionError>()
//                }, // through overflow
//            )
//        }
//    }
//
//    @Test
//    fun testShortMulOverflow() {
//        withTreatingOverflowAsError {
//            checkWithExceptionExecutionMatches(
//                OverflowExamples::shortMulOverflow,
//                eq(2),
//                { _, _, r -> r != null && !r.isException<OverflowDetectionError>() },
//                { _, _, r -> r.isException<OverflowDetectionError>() }, // through overflow
//            )
//        }
//    }
//
//    @Test
//    fun testIntAddOverflow() {
//        withTreatingOverflowAsError {
//            checkWithExceptionExecutionMatches(
//                OverflowExamples::intAddOverflow,
//                eq(2),
//                { _, _, r -> r != null && !r.isException<OverflowDetectionError>() },
//                { _, x, y, r ->
//                    val negOverflow = ((x + y) >= 0 && x < 0 && y < 0)
//                    val posOverflow = ((x + y) <= 0 && x > 0 && y > 0)
//                    (negOverflow || posOverflow) && r.isException<OverflowDetectionError>()
//                }, // through overflow
//            )
//        }
//    }
//
//    @Test
//    fun testIntSubOverflow() {
//        withTreatingOverflowAsError {
//            checkWithExceptionExecutionMatches(
//                OverflowExamples::intSubOverflow,
//                eq(2),
//                { _, _, r -> r != null && !r.isException<OverflowDetectionError>() },
//                { _, x, y, r ->
//                    val negOverflow = ((x - y) >= 0 && x < 0 && y > 0)
//                    val posOverflow = ((x - y) <= 0 && x > 0 && y < 0)
//                    (negOverflow || posOverflow) && r.isException<OverflowDetectionError>()
//                }, // through overflow
//            )
//        }
//    }
//
//    @Test
//    fun testIntMulOverflow() {
//        // This test has solver timeout.
//        // Reason: softConstraints, containing limits for Int values, hang solver.
//        // With solver timeout softConstraints are dropped and hard constraints are SAT for overflow.
//        withSolverTimeoutInMillis(timeoutInMillis = 1000) {
//            withTreatingOverflowAsError {
//                checkWithExceptionExecutionMatches(
//                    OverflowExamples::intMulOverflow,
//                    eq(2),
//                    { _, _, r -> r != null && !r.isException<OverflowDetectionError>() },
//                    { _, _, r -> r.isException<OverflowDetectionError>() }, // through overflow
//                )
//            }
//        }
//    }
//
//    @Test
//    fun testLongAddOverflow() {
//        withTreatingOverflowAsError {
//            checkWithExceptionExecutionMatches(
//                OverflowExamples::longAddOverflow,
//                eq(2),
//                { _, _, r -> r != null && !r.isException<OverflowDetectionError>() },
//                { _, x, y, r ->
//                    val negOverflow = ((x + y) >= 0 && x < 0 && y < 0)
//                    val posOverflow = ((x + y) <= 0 && x > 0 && y > 0)
//                    (negOverflow || posOverflow) && r.isException<OverflowDetectionError>()
//                }, // through overflow
//            )
//        }
//    }
//
//    @Test
//    fun testLongSubOverflow() {
//        withTreatingOverflowAsError {
//            checkWithExceptionExecutionMatches(
//                OverflowExamples::longSubOverflow,
//                eq(2),
//                { _, _, r -> r != null && !r.isException<OverflowDetectionError>() },
//                { _, x, y, r ->
//                    val negOverflow = ((x - y) >= 0 && x < 0 && y > 0)
//                    val posOverflow = ((x - y) <= 0 && x > 0 && y < 0)
//                    (negOverflow || posOverflow) && r.isException<OverflowDetectionError>()
//                }, // through overflow
//            )
//        }
//    }
//
//    @Test
//    @Disabled("Flaky branch count mismatch (1 instead of 2)")
//    fun testLongMulOverflow() {
//        // This test has solver timeout.
//        // Reason: softConstraints, containing limits for Int values, hang solver.
//        // With solver timeout softConstraints are dropped and hard constraints are SAT for overflow.
//        withSolverTimeoutInMillis(timeoutInMillis = 2000) {
//            withTreatingOverflowAsError {
//                checkWithExceptionExecutionMatches(
//                    OverflowExamples::longMulOverflow,
//                    eq(2),
//                    { _, _, r -> r != null && !r.isException<OverflowDetectionError>() },
//                    { _, _, r -> r.isException<OverflowDetectionError>() }, // through overflow
//                )
//            }
//        }
//    }
//
//    @Test
//    fun testIncOverflow() {
//        withTreatingOverflowAsError {
//            checkWithExceptionExecutionMatches(
//                OverflowExamples::incOverflow,
//                eq(2),
//                { _, r -> r != null && !r.isException<OverflowDetectionError>() },
//                { _, r -> r.isException<OverflowDetectionError>() }, // through overflow
//            )
//        }
//    }
//
//    @Test
//    fun testIntCubeOverflow() {
//        val sqrtIntMax = floor(sqrt(Int.MAX_VALUE.toDouble())).toInt()
//        withTreatingOverflowAsError {
//            checkWithExceptionExecutionMatches(
//                OverflowExamples::intCubeOverflow,
//                eq(3),
//                { _, r -> r != null && !r.isException<ArithmeticException>() },
//                // Can't use abs(x) below, because abs(Int.MIN_VALUE) == Int.MIN_VALUE.
//                // (Int.MAX_VALUE shr 16) is the border of square overflow and cube overflow.
//                // Int.MAX_VALUE.toDouble().pow(1/3.toDouble())
//                { _, x, r -> (x > -sqrtIntMax && x < sqrtIntMax) && r.isException<OverflowDetectionError>() }, // through overflow
//                { _, x, r -> (x <= -sqrtIntMax || x >= sqrtIntMax) && r.isException<OverflowDetectionError>() }, // through overflow
//            )
//        }
//    }
//
//    //  Generated Kotlin code does not compile, so disabled for now
//    @Test
//    @Disabled
//    fun testQuickSort() {
//        withTreatingOverflowAsError {
//            checkWithExceptionExecutionMatches(
//                Sort::quickSort,
//                ignoreNumberOfAnalysisResults,
//                { _, _, _, r -> r != null && !r.isException<OverflowDetectionError>() },
//                { _, _, _, r -> r.isException<OverflowDetectionError>() }, // through overflow
//            )
//        }
//    }
//
//    @Test
//    fun testIntOverflowWithoutError() {
//        checkExecutionMatches(
//            OverflowExamples::intOverflow,
//            eq(6),
//            { _, x, _, r -> x * x * x <= 0 && x <= 0 && r == 0 },
//            { _, x, _, r -> x * x * x > 0 && x <= 0 && r == 0 }, // through overflow
//            { _, x, y, r -> x * x * x > 0 && x > 0 && y != 10 && r == 0 },
//            { _, x, y, r -> x * x * x > 0 && x > 0 && y == 10 && r == 1 },
//            { _, x, y, r -> x * x * x <= 0 && x > 0 && y != 20 && r == 0 }, // through overflow
//            { _, x, y, r -> x * x * x <= 0 && x > 0 && y == 20 && r == 2 } // through overflow
//        )
//    }
//}
