package org.usvm.samples.stream

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.isException

import java.util.OptionalDouble
import java.util.OptionalLong
import java.util.stream.LongStream
import kotlin.streams.toList

class LongStreamExampleTest : JavaMethodTestRunner() {
    @Test
    fun testReturningStreamAsParameterExample() {
        checkExecutionMatches(
            LongStreamExample::returningStreamAsParameterExample,
            { _, s, r -> s != null && s.toList() == r!!.toList() },
        )
    }

    @Test
    fun testUseParameterStream() {
        checkExecutionMatches(
            LongStreamExample::useParameterStream,
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
            LongStreamExample::filterExample,
            { _, c, r -> null !in c && r == false },
            { _, c, r -> null in c && r == true },
        )
    }

    @Test
    fun testMapExample() {
        checkExecutionMatches(
            LongStreamExample::mapExample,
            { _, c, r -> null in c && r.contentEquals(c.longs { it?.toLong()?.times(2) ?: 0L }) },
            { _, c: List<Short?>, r -> null !in c && r.contentEquals(c.longs { it?.toLong()?.times(2) ?: 0L }) },
        )
    }

    @Test
    fun testMapToObjExample() {
        checkExecutionMatches(
            LongStreamExample::mapToObjExample,
            { _, c, r ->
                val intArrays = c.longs().map { it.let { i -> longArrayOf(i, i) } }.toTypedArray()

                null in c && intArrays.zip(r as Array<out Any>).all { it.first.contentEquals(it.second as LongArray?) }
            },
            { _, c: List<Short?>, r ->
                val intArrays = c.longs().map { it.let { i -> longArrayOf(i, i) } }.toTypedArray()

                null !in c && intArrays.zip(r as Array<out Any>).all { it.first.contentEquals(it.second as LongArray?) }
            },
        )
    }

    @Test
    fun testMapToIntExample() {
        checkExecutionMatches(
            LongStreamExample::mapToIntExample,
            { _, c, r ->
                val ints = c.longs().map { it.toInt() }.toIntArray()

                null in c && ints.contentEquals(r)
            },
            { _, c: List<Short?>, r ->
                val ints = c.longs().map { it.toInt() }.toIntArray()

                null !in c && ints.contentEquals(r)
            },
        )
    }

    @Test
    fun testMapToDoubleExample() {
        checkExecutionMatches(
            LongStreamExample::mapToDoubleExample,
            { _, c, r ->
                val doubles = c.longs().map { it.toDouble() / 2 }.toDoubleArray()

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
            LongStreamExample::flatMapExample,
            { _, c, r ->
                val intLists = c.map {
                    (it?.toLong() ?: 0L).let { i -> listOf(i, i) }
                }

                r!!.contentEquals(intLists.flatten().toLongArray())
            },
        )
    }

    @Test
    fun testDistinctExample() {
        checkExecutionMatches(
            LongStreamExample::distinctExample,
            { _, c, r ->
                val longs = c.longs()

                longs.contentEquals(longs.distinct().toLongArray()) && r == false
            },
            { _, c, r ->
                val longs = c.longs()

                !longs.contentEquals(longs.distinct().toLongArray()) && r == true
            },
        )
    }

    @Test
    @Tag("slow")
    // TODO slow sorting https://github.com/UnitTestBot/UTBotJava/issues/188
    fun testSortedExample() {
        checkExecutionMatches(
            LongStreamExample::sortedExample,
            { _, c, r -> c.last() < c.first() && r!!.asSequence().isSorted() }
        )
    }

    // TODO unsupported
//    @Test
//    fun testPeekExample() {
//        checkThisAndStaticsAfter(
//            LongStreamExample::peekExample,
//            ignoreNumberOfAnalysisResults,
//            *streamConsumerStaticsMatchers,
//        )
//    }

    @Test
    fun testLimitExample() {
        checkExecutionMatches(
            LongStreamExample::limitExample,
            { _, c, r -> c.size <= 2 && c.longs().contentEquals(r) },
            { _, c, r -> c.size > 2 && c.take(2).longs().contentEquals(r) },
        )
    }

    @Test
    fun testSkipExample() {
        checkExecutionMatches(
            LongStreamExample::skipExample,
            { _, c, r -> c.size > 2 && c.drop(2).longs().contentEquals(r) },
            { _, c, r -> c.size <= 2 && r!!.isEmpty() },
        )
    }

// TODO unsupported
//    @Test
//    fun testForEachExample() {
//        checkThisAndStaticsAfter(
//            LongStreamExample::forEachExample,
//            ignoreNumberOfAnalysisResults,
//            *streamConsumerStaticsMatchers,
//        )
//    }

    @Test
    fun testToArrayExample() {
        checkExecutionMatches(
            LongStreamExample::toArrayExample,
            { _, c, r -> c.longs().contentEquals(r) },
        )
    }

    @Test
    fun testReduceExample() {
        checkExecutionMatches(
            LongStreamExample::reduceExample,
            { _, c, r -> c.isEmpty() && r == 42L },
            { _, c: List<Short?>, r -> c.isNotEmpty() && r == c.filterNotNull().sum() + 42L },
        )
    }

    @Test
    fun testOptionalReduceExample() {
        checkWithExceptionExecutionMatches(
            LongStreamExample::optionalReduceExample,
            { _, c, r -> c.isEmpty() && r.getOrThrow() == OptionalLong.empty() },
            { _, c: List<Short?>, r ->
                c.isNotEmpty() && r.getOrThrow() == OptionalLong.of(
                    c.filterNotNull().sum().toLong()
                )
            },
        )
    }

    @Test
    fun testSumExample() {
        checkExecutionMatches(
            LongStreamExample::sumExample,
            { _, c, r -> c.isEmpty() && r == 0L },
            { _, c, r -> c.isNotEmpty() && c.filterNotNull().sum().toLong() == r },
        )
    }

    @Test
    fun testMinExample() {
        checkWithExceptionExecutionMatches(
            LongStreamExample::minExample,
            { _, c, r -> c.isEmpty() && r.getOrThrow() == OptionalLong.empty() },
            { _, c, r ->
                c.isNotEmpty() && r.getOrThrow() == OptionalLong.of(c.mapNotNull { it.toLong() }.minOrNull()!!)
            },
        )
    }

    @Test
    fun testMaxExample() {
        checkWithExceptionExecutionMatches(
            LongStreamExample::maxExample,
            { _, c, r -> c.isEmpty() && r.getOrThrow() == OptionalLong.empty() },
            { _, c, r ->
                c.isNotEmpty() && r.getOrThrow() == OptionalLong.of(c.mapNotNull { it.toLong() }.maxOrNull()!!)
            },
        )
    }

    @Test
    fun testCountExample() {
        checkExecutionMatches(
            LongStreamExample::countExample,
            { _, c, r -> c.isEmpty() && r == 0L },
            { _, c, r -> c.isNotEmpty() && c.size.toLong() == r },
        )
    }

    @Test
    fun testAverageExample() {
        checkExecutionMatches(
            LongStreamExample::averageExample,
            { _, c, r -> c.isEmpty() && r == OptionalDouble.empty() },
            { _, c, r -> c.isNotEmpty() && c.mapNotNull { it.toLong() }.average() == r!!.asDouble },
        )
    }

    @Test
    fun testSummaryStatisticsExample() {
        checkExecutionMatches(
            LongStreamExample::summaryStatisticsExample,
            { _, c, r ->
                val sum = r!!.sum
                val count = r.count
                val min = r.min
                val max = r.max

                val allStatisticsAreCorrect = sum == 0L &&
                        count == 0L &&
                        min == Long.MAX_VALUE &&
                        max == Long.MIN_VALUE

                c.isEmpty() && allStatisticsAreCorrect
            },
            { _, c, r ->
                val sum = r!!.sum
                val count = r.count
                val min = r.min
                val max = r.max

                val longs = c.longs()

                val allStatisticsAreCorrect = sum == longs.sum() &&
                        count == longs.size.toLong() &&
                        min == longs.minOrNull() &&
                        max == longs.maxOrNull()

                c.isNotEmpty() && allStatisticsAreCorrect
            },
        )
    }

    @Test
    fun testAnyMatchExample() {
        checkExecutionMatches(
            LongStreamExample::anyMatchExample,
            { _, c, r -> c.isEmpty() && r == false },
            { _, c, r -> c.isNotEmpty() && c.longs().all { it == 0L } && r == false },
            { _, c, r ->
                val longs = c.longs()

                c.isNotEmpty() && longs.first() != 0L && longs.last() == 0L && r == true
            },
            { _, c, r ->
                val longs = c.longs()

                c.isNotEmpty() && longs.first() == 0L && longs.last() != 0L && r == true
            },
            { _, c, r ->
                val longs = c.longs()

                c.isNotEmpty() && longs.none { it == 0L } && r == true
            },
        )
    }

    @Test
    fun testAllMatchExample() {
        checkExecutionMatches(
            LongStreamExample::allMatchExample,
            { _, c, r -> c.isEmpty() && r == true },
            { _, c, r -> c.isNotEmpty() && c.longs().all { it == 0L } && r == false },
            { _, c, r ->
                val longs = c.longs()

                c.isNotEmpty() && longs.first() != 0L && longs.last() == 0L && r == false
            },
            { _, c, r ->
                val longs = c.longs()

                c.isNotEmpty() && longs.first() == 0L && longs.last() != 0L && r == false
            },
            { _, c, r ->
                val longs = c.longs()

                c.isNotEmpty() && longs.none { it == 0L } && r == true
            },
        )
    }

    @Test
    fun testNoneMatchExample() {
        checkExecutionMatches(
            LongStreamExample::noneMatchExample,
            { _, c, r -> c.isEmpty() && r == true },
            { _, c, r -> c.isNotEmpty() && c.longs().all { it == 0L } && r == true },
            { _, c, r ->
                val longs = c.longs()

                c.isNotEmpty() && longs.first() != 0L && longs.last() == 0L && r == false
            },
            { _, c, r ->
                val longs = c.longs()

                c.isNotEmpty() && longs.first() == 0L && longs.last() != 0L && r == false
            },
            { _, c, r ->
                val longs = c.longs()

                c.isNotEmpty() && longs.none { it == 0L } && r == false
            },
        )
    }

    @Test
    fun testFindFirstExample() {
        checkExecutionMatches(
            LongStreamExample::findFirstExample,
            { _, c, r -> c.isEmpty() && r == OptionalLong.empty() },
            { _, c, r -> c.isNotEmpty() && r == OptionalLong.of(c.longs().first()) },
        )
    }

    @Test
    fun testAsDoubleStreamExample() {
        checkExecutionMatches(
            LongStreamExample::asDoubleStreamExample,
            { _, c, r -> c.longs().map { it.toDouble() }.toList() == r!!.toList() },
        )
    }

    @Test
    fun testBoxedExample() {
        checkExecutionMatches(
            LongStreamExample::boxedExample,
            { _, c, r -> c.longs().toList() == r!!.toList() },
        )
    }

    @Test
    fun testIteratorExample() {
        checkExecutionMatches(
            LongStreamExample::iteratorSumExample,
            { _, c, r -> c.isEmpty() && r == 0L },
            { _, c: List<Short?>, r -> c.isNotEmpty() && c.longs().sum() == r },
        )
    }

    @Test
    fun testStreamOfExample() {
        checkExecutionMatches(
            LongStreamExample::streamOfExample,
            // NOTE: the order of the matchers is important because Stream could be used only once
            { _, c, r -> c.isNotEmpty() && c.contentEquals(r!!.toArray()) },
            { _, c, r -> c.isEmpty() && LongStream.empty().toArray().contentEquals(r!!.toArray()) },
        )
    }

    @Test
    fun testClosedStreamExample() {
        checkWithExceptionExecutionMatches(
            LongStreamExample::closedStreamExample,
            { _, _, r -> r.isException<IllegalStateException>() },
        )
    }

    @Test
    fun testGenerateExample() {
        checkExecutionMatches(
            LongStreamExample::generateExample,
            { _, r -> r!!.contentEquals(LongArray(10) { 42L }) }
        )
    }

    @Test
    fun testIterateExample() {
        checkExecutionMatches(
            LongStreamExample::iterateExample,
            { _, r -> r!!.contentEquals(LongArray(10) { i -> 42L + i }) }
        )
    }

    @Test
    fun testConcatExample() {
        checkExecutionMatches(
            LongStreamExample::concatExample,
            { _, r -> r!!.contentEquals(LongArray(10) { 42L } + LongArray(10) { i -> 42L + i }) }
        )
    }

    @Test
    fun testRangeExample() {
        checkExecutionMatches(
            LongStreamExample::rangeExample,
            { _, r -> r!!.contentEquals(LongArray(10) { it.toLong() }) }
        )
    }

    @Test
    fun testRangeClosedExample() {
        checkExecutionMatches(
            LongStreamExample::rangeClosedExample,
            { _, r -> r!!.contentEquals(LongArray(11) { it.toLong() }) }
        )
    }
}

private fun List<Short?>.longs(mapping: (Short?) -> Long = { it?.toLong() ?: 0L }): LongArray =
    map { mapping(it) }.toLongArray()
