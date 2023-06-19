package org.usvm.samples.stream

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.isException

import java.util.OptionalDouble
import java.util.OptionalInt
import java.util.stream.IntStream
import kotlin.streams.toList

class IntStreamExampleTest : JavaMethodTestRunner() {
    @Test
    fun testReturningStreamAsParameterExample() {
        checkExecutionMatches(
            IntStreamExample::returningStreamAsParameterExample,
            { _, s, r -> s != null && s.toList() == r!!.toList() },
        )
    }

    @Test
    fun testUseParameterStream() {
        checkExecutionMatches(
            IntStreamExample::useParameterStream,
            { _, s, r -> s.toArray().isEmpty() && r == 0 },
            { _, s, r ->
                s.toArray().let {
                    it.isNotEmpty() && r == it.size
                }
            },
        )
    }

    @Test
    fun testFilterExample() {
        checkExecutionMatches(
            IntStreamExample::filterExample,
            { _, c, r -> null !in c && r == false },
            { _, c, r -> null in c && r == true },
        )
    }

    @Test
    fun testMapExample() {
        checkExecutionMatches(
            IntStreamExample::mapExample,
            { _, c, r -> null in c && r.contentEquals(c.ints { it?.toInt()?.times(2) ?: 0 }) },
            { _, c: List<Short?>, r -> null !in c && r.contentEquals(c.ints { it?.toInt()?.times(2) ?: 0 }) },
        )
    }

    @Test
    fun testMapToObjExample() {
        checkExecutionMatches(
            IntStreamExample::mapToObjExample,
            { _, c, r ->
                val intArrays = c.ints().map { it.let { i -> intArrayOf(i, i) } }.toTypedArray()

                null in c && intArrays.zip(r as Array<out Any>).all { it.first.contentEquals(it.second as IntArray?) }
            },
            { _, c: List<Short?>, r ->
                val intArrays = c.ints().map { it.let { i -> intArrayOf(i, i) } }.toTypedArray()

                null !in c && intArrays.zip(r as Array<out Any>).all { it.first.contentEquals(it.second as IntArray?) }
            },
        )
    }

    @Test
    fun testMapToLongExample() {
        checkExecutionMatches(
            IntStreamExample::mapToLongExample,
            { _, c, r ->
                val longs = c.ints().map { it.toLong() * 2 }.toLongArray()

                null in c && longs.contentEquals(r)
            },
            { _, c: List<Short?>, r ->
                val longs = c.ints().map { it.toLong() * 2 }.toLongArray()

                null !in c && longs.contentEquals(r)
            },
        )
    }

    @Test
    fun testMapToDoubleExample() {
        checkExecutionMatches(
            IntStreamExample::mapToDoubleExample,
            { _, c, r ->
                val doubles = c.ints().map { it.toDouble() / 2 }.toDoubleArray()

                null in c && doubles.contentEquals(r)
            },
            { _, c: List<Short?>, r ->
                val doubles = c.filterNotNull().map { it.toDouble() / 2 }.toDoubleArray()

                null !in c && doubles.contentEquals(r)
            },
        )
    }

    @Test
    fun testFlatMapExample() {
        checkExecutionMatches(
            IntStreamExample::flatMapExample,
            { _, c, r ->
                val intLists = c.mapNotNull {
                    it.toInt().let { i -> listOf(i, i) }
                }

                r!!.contentEquals(intLists.flatten().toIntArray())
            },
        )
    }

    @Test
    fun testDistinctExample() {
        checkExecutionMatches(
            IntStreamExample::distinctExample,
            { _, c, r ->
                val ints = c.ints()

                ints.contentEquals(ints.distinct().toIntArray()) && r == false
            },
            { _, c, r ->
                val ints = c.ints()

                !ints.contentEquals(ints.distinct().toIntArray()) && r == true
            },
        )
    }

    @Test
    @Tag("slow")
    // TODO slow sorting https://github.com/UnitTestBot/UTBotJava/issues/188
    fun testSortedExample() {
        checkExecutionMatches(
            IntStreamExample::sortedExample,
            { _, c, r -> c.last() < c.first() && r!!.asSequence().isSorted() }
        )
    }

    // TODO unsupported
//    @Test
//    fun testPeekExample() {
//        checkThisAndStaticsAfter(
//            IntStreamExample::peekExample,
//            ignoreNumberOfAnalysisResults,
//            *streamConsumerStaticsMatchers,
//        )
//    }

    @Test
    fun testLimitExample() {
        checkExecutionMatches(
            IntStreamExample::limitExample,
            { _, c, r -> c.size <= 2 && c.ints().contentEquals(r) },
            { _, c, r -> c.size > 2 && c.take(2).ints().contentEquals(r) },
        )
    }

    @Test
    fun testSkipExample() {
        checkExecutionMatches(
            IntStreamExample::skipExample,
            { _, c, r -> c.size > 2 && c.drop(2).ints().contentEquals(r) },
            { _, c, r -> c.size <= 2 && r!!.isEmpty() },
        )
    }

    // TODO unsupported
//    @Test
//    fun testForEachExample() {
//        checkThisAndStaticsAfter(
//            IntStreamExample::forEachExample,
//            ignoreNumberOfAnalysisResults,
//            *streamConsumerStaticsMatchers,
//        )
//    }

    @Test
    fun testToArrayExample() {
        checkExecutionMatches(
            IntStreamExample::toArrayExample,
            { _, c, r -> c.ints().contentEquals(r) },
        )
    }

    @Test
    fun testReduceExample() {
        checkExecutionMatches(
            IntStreamExample::reduceExample,
            { _, c, r -> c.isEmpty() && r == 42 },
            { _, c: List<Short?>, r -> c.isNotEmpty() && r == c.filterNotNull().sum() + 42 },
        )
    }

    @Test
    fun testOptionalReduceExample() {
        checkWithExceptionExecutionMatches(
            IntStreamExample::optionalReduceExample,
            { _, c, r -> c.isEmpty() && r.getOrThrow() == OptionalInt.empty() },
            { _, c: List<Short?>, r -> c.isNotEmpty() && r.getOrThrow() == OptionalInt.of(c.filterNotNull().sum()) },
        )
    }

    @Test
    fun testSumExample() {
        checkExecutionMatches(
            IntStreamExample::sumExample,
            { _, c, r -> c.isEmpty() && r == 0 },
            { _, c, r -> c.isNotEmpty() && c.filterNotNull().sum() == r },
        )
    }

    @Test
    fun testMinExample() {
        checkWithExceptionExecutionMatches(
            IntStreamExample::minExample,
            { _, c, r -> c.isEmpty() && r.getOrThrow() == OptionalInt.empty() },
            { _, c, r ->
                c.isNotEmpty() && r.getOrThrow() == OptionalInt.of(c.mapNotNull { it.toInt() }.minOrNull()!!)
            },
        )
    }

    @Test
    fun testMaxExample() {
        checkWithExceptionExecutionMatches(
            IntStreamExample::maxExample,
            { _, c, r -> c.isEmpty() && r.getOrThrow() == OptionalInt.empty() },
            { _, c, r ->
                c.isNotEmpty() && r.getOrThrow() == OptionalInt.of(c.mapNotNull { it.toInt() }.maxOrNull()!!)
            },
        )
    }

    @Test
    fun testCountExample() {
        checkExecutionMatches(
            IntStreamExample::countExample,
            { _, c, r -> c.isEmpty() && r == 0L },
            { _, c, r -> c.isNotEmpty() && c.size.toLong() == r },
        )
    }

    @Test
    fun testAverageExample() {
        checkExecutionMatches(
            IntStreamExample::averageExample,
            { _, c, r -> c.isEmpty() && r == OptionalDouble.empty() },
            { _, c, r -> c.isNotEmpty() && c.mapNotNull { it.toInt() }.average() == r!!.asDouble },
        )
    }

    @Test
    fun testSummaryStatisticsExample() {
        checkExecutionMatches(
            IntStreamExample::summaryStatisticsExample,
            { _, c, r ->
                val sum = r!!.sum
                val count = r.count
                val min = r.min
                val max = r.max

                val allStatisticsAreCorrect = sum == 0L &&
                        count == 0L &&
                        min == Int.MAX_VALUE &&
                        max == Int.MIN_VALUE

                c.isEmpty() && allStatisticsAreCorrect
            },
            { _, c, r ->
                val sum = r!!.sum
                val count = r.count
                val min = r.min
                val max = r.max

                val ints = c.ints()

                val allStatisticsAreCorrect = sum == ints.sum().toLong() &&
                        count == ints.size.toLong() &&
                        min == ints.minOrNull() &&
                        max == ints.maxOrNull()

                c.isNotEmpty() && allStatisticsAreCorrect
            },
        )
    }

    @Test
    fun testAnyMatchExample() {
        checkExecutionMatches(
            IntStreamExample::anyMatchExample,
            { _, c, r -> c.isEmpty() && r == false },
            { _, c, r -> c.isNotEmpty() && c.ints().all { it == 0 } && r == false },
            { _, c, r ->
                val ints = c.ints()

                c.isNotEmpty() && ints.first() != 0 && ints.last() == 0 && r == true
            },
            { _, c, r ->
                val ints = c.ints()

                c.isNotEmpty() && ints.first() == 0 && ints.last() != 0 && r == true
            },
            { _, c, r ->
                val ints = c.ints()

                c.isNotEmpty() && ints.none { it == 0 } && r == true
            },
        )
    }

    @Test
    fun testAllMatchExample() {
        checkExecutionMatches(
            IntStreamExample::allMatchExample,
            { _, c, r -> c.isEmpty() && r == true },
            { _, c, r -> c.isNotEmpty() && c.ints().all { it == 0 } && r == false },
            { _, c, r ->
                val ints = c.ints()

                c.isNotEmpty() && ints.first() != 0 && ints.last() == 0 && r == false
            },
            { _, c, r ->
                val ints = c.ints()

                c.isNotEmpty() && ints.first() == 0 && ints.last() != 0 && r == false
            },
            { _, c, r ->
                val ints = c.ints()

                c.isNotEmpty() && ints.none { it == 0 } && r == true
            },
        )
    }

    @Test
    fun testNoneMatchExample() {
        checkExecutionMatches(
            IntStreamExample::noneMatchExample,
            { _, c, r -> c.isEmpty() && r == true },
            { _, c, r -> c.isNotEmpty() && c.ints().all { it == 0 } && r == true },
            { _, c, r ->
                val ints = c.ints()

                c.isNotEmpty() && ints.first() != 0 && ints.last() == 0 && r == false
            },
            { _, c, r ->
                val ints = c.ints()

                c.isNotEmpty() && ints.first() == 0 && ints.last() != 0 && r == false
            },
            { _, c, r ->
                val ints = c.ints()

                c.isNotEmpty() && ints.none { it == 0 } && r == false
            },
        )
    }

    @Test
    fun testFindFirstExample() {
        checkExecutionMatches(
            IntStreamExample::findFirstExample,
            { _, c, r -> c.isEmpty() && r == OptionalInt.empty() },
            { _, c, r -> c.isNotEmpty() && r == OptionalInt.of(c.ints().first()) },
        )
    }

    @Test
    fun testAsLongStreamExample() {
        checkExecutionMatches(
            IntStreamExample::asLongStreamExample,
            { _, c, r -> c.ints().map { it.toLong() }.toList() == r!!.toList() },
        )
    }

    @Test
    fun testAsDoubleStreamExample() {
        checkExecutionMatches(
            IntStreamExample::asDoubleStreamExample,
            { _, c, r -> c.ints().map { it.toDouble() }.toList() == r!!.toList() },
        )
    }

    @Test
    fun testBoxedExample() {
        checkExecutionMatches(
            IntStreamExample::boxedExample,
            { _, c, r -> c.ints().toList() == r!!.toList() },
        )
    }

    @Test
    fun testIteratorExample() {
        checkExecutionMatches(
            IntStreamExample::iteratorSumExample,
            { _, c, r -> c.isEmpty() && r == 0 },
            { _, c: List<Short?>, r -> c.isNotEmpty() && c.ints().sum() == r },
        )
    }

    @Test
    fun testStreamOfExample() {
        checkExecutionMatches(
            IntStreamExample::streamOfExample,
            // NOTE: the order of the matchers is important because Stream could be used only once
            { _, c, r -> c.isNotEmpty() && c.contentEquals(r!!.toArray()) },
            { _, c, r -> c.isEmpty() && IntStream.empty().toArray().contentEquals(r!!.toArray()) },
        )
    }

    @Test
    fun testClosedStreamExample() {
        checkWithExceptionExecutionMatches(
            IntStreamExample::closedStreamExample,
            { _, _, r -> r.isException<IllegalStateException>() },
        )
    }

    @Test
    fun testGenerateExample() {
        checkExecutionMatches(
            IntStreamExample::generateExample,
            { _, r -> r!!.contentEquals(IntArray(10) { 42 }) }
        )
    }

    @Test
    fun testIterateExample() {
        checkExecutionMatches(
            IntStreamExample::iterateExample,
            { _, r -> r!!.contentEquals(IntArray(10) { i -> 42 + i }) }
        )
    }

    @Test
    fun testConcatExample() {
        checkExecutionMatches(
            IntStreamExample::concatExample,
            { _, r -> r!!.contentEquals(IntArray(10) { 42 } + IntArray(10) { i -> 42 + i }) }
        )
    }

    @Test
    fun testRangeExample() {
        checkExecutionMatches(
            IntStreamExample::rangeExample,
            { _, r -> r!!.contentEquals(IntArray(10) { it }) }
        )
    }

    @Test
    fun testRangeClosedExample() {
        checkExecutionMatches(
            IntStreamExample::rangeClosedExample,
            { _, r -> r!!.contentEquals(IntArray(11) { it }) }
        )
    }
}

private fun List<Short?>.ints(mapping: (Short?) -> Int = { it?.toInt() ?: 0 }): IntArray =
    map { mapping(it) }.toIntArray()
