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
        checkDiscoveredProperties(
            IntStreamExample::returningStreamAsParameterExample,
            eq(1),
            { _, s, r -> s != null && s.toList() == r!!.toList() },
        )
    }

    @Test
    fun testUseParameterStream() {
        checkDiscoveredProperties(
            IntStreamExample::useParameterStream,
            eq(2),
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
        checkDiscoveredProperties(
            IntStreamExample::filterExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> null !in c && r == false },
            { _, c, r -> null in c && r == true },
        )
    }

    @Test
    fun testMapExample() {
        checkDiscoveredProperties(
            IntStreamExample::mapExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> null in c && r.contentEquals(c.ints { it?.toInt()?.times(2) ?: 0 }) },
            { _, c: List<Short?>, r -> null !in c && r.contentEquals(c.ints { it?.toInt()?.times(2) ?: 0 }) },
        )
    }

    @Test
    fun testMapToObjExample() {
        checkDiscoveredProperties(
            IntStreamExample::mapToObjExample,
            ignoreNumberOfAnalysisResults,
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
        checkDiscoveredProperties(
            IntStreamExample::mapToLongExample,
            ignoreNumberOfAnalysisResults,
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
        checkDiscoveredProperties(
            IntStreamExample::mapToDoubleExample,
            ignoreNumberOfAnalysisResults,
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
        checkDiscoveredProperties(
            IntStreamExample::flatMapExample,
            ignoreNumberOfAnalysisResults,
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
        checkDiscoveredProperties(
            IntStreamExample::distinctExample,
            ignoreNumberOfAnalysisResults,
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
        checkDiscoveredProperties(
            IntStreamExample::sortedExample,
            ignoreNumberOfAnalysisResults,
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
        checkDiscoveredProperties(
            IntStreamExample::limitExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.size <= 2 && c.ints().contentEquals(r) },
            { _, c, r -> c.size > 2 && c.take(2).ints().contentEquals(r) },
        )
    }

    @Test
    fun testSkipExample() {
        checkDiscoveredProperties(
            IntStreamExample::skipExample,
            ignoreNumberOfAnalysisResults,
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
        checkDiscoveredProperties(
            IntStreamExample::toArrayExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.ints().contentEquals(r) },
        )
    }

    @Test
    fun testReduceExample() {
        checkDiscoveredProperties(
            IntStreamExample::reduceExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r == 42 },
            { _, c: List<Short?>, r -> c.isNotEmpty() && r == c.filterNotNull().sum() + 42 },
        )
    }

    @Test
    fun testOptionalReduceExample() {
        checkDiscoveredPropertiesWithExceptions(
            IntStreamExample::optionalReduceExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r.getOrThrow() == OptionalInt.empty() },
            { _, c: List<Short?>, r -> c.isNotEmpty() && r.getOrThrow() == OptionalInt.of(c.filterNotNull().sum()) },
        )
    }

    @Test
    fun testSumExample() {
        checkDiscoveredProperties(
            IntStreamExample::sumExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r == 0 },
            { _, c, r -> c.isNotEmpty() && c.filterNotNull().sum() == r },
        )
    }

    @Test
    fun testMinExample() {
        checkDiscoveredPropertiesWithExceptions(
            IntStreamExample::minExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r.getOrThrow() == OptionalInt.empty() },
            { _, c, r ->
                c.isNotEmpty() && r.getOrThrow() == OptionalInt.of(c.mapNotNull { it.toInt() }.minOrNull()!!)
            },
        )
    }

    @Test
    fun testMaxExample() {
        checkDiscoveredPropertiesWithExceptions(
            IntStreamExample::maxExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r.getOrThrow() == OptionalInt.empty() },
            { _, c, r ->
                c.isNotEmpty() && r.getOrThrow() == OptionalInt.of(c.mapNotNull { it.toInt() }.maxOrNull()!!)
            },
        )
    }

    @Test
    fun testCountExample() {
        checkDiscoveredProperties(
            IntStreamExample::countExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r == 0L },
            { _, c, r -> c.isNotEmpty() && c.size.toLong() == r },
        )
    }

    @Test
    fun testAverageExample() {
        checkDiscoveredProperties(
            IntStreamExample::averageExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r == OptionalDouble.empty() },
            { _, c, r -> c.isNotEmpty() && c.mapNotNull { it.toInt() }.average() == r!!.asDouble },
        )
    }

    @Test
    fun testSummaryStatisticsExample() {
        checkDiscoveredProperties(
            IntStreamExample::summaryStatisticsExample,
            ignoreNumberOfAnalysisResults,
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
        checkDiscoveredProperties(
            IntStreamExample::anyMatchExample,
            ignoreNumberOfAnalysisResults,
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
        checkDiscoveredProperties(
            IntStreamExample::allMatchExample,
            ignoreNumberOfAnalysisResults,
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
        checkDiscoveredProperties(
            IntStreamExample::noneMatchExample,
            ignoreNumberOfAnalysisResults,
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
        checkDiscoveredProperties(
            IntStreamExample::findFirstExample,
            eq(3),
            { _, c, r -> c.isEmpty() && r == OptionalInt.empty() },
            { _, c, r -> c.isNotEmpty() && r == OptionalInt.of(c.ints().first()) },
        )
    }

    @Test
    fun testAsLongStreamExample() {
        checkDiscoveredProperties(
            IntStreamExample::asLongStreamExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.ints().map { it.toLong() }.toList() == r!!.toList() },
        )
    }

    @Test
    fun testAsDoubleStreamExample() {
        checkDiscoveredProperties(
            IntStreamExample::asDoubleStreamExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.ints().map { it.toDouble() }.toList() == r!!.toList() },
        )
    }

    @Test
    fun testBoxedExample() {
        checkDiscoveredProperties(
            IntStreamExample::boxedExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.ints().toList() == r!!.toList() },
        )
    }

    @Test
    fun testIteratorExample() {
        checkDiscoveredProperties(
            IntStreamExample::iteratorSumExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r == 0 },
            { _, c: List<Short?>, r -> c.isNotEmpty() && c.ints().sum() == r },
        )
    }

    @Test
    fun testStreamOfExample() {
        checkDiscoveredProperties(
            IntStreamExample::streamOfExample,
            ignoreNumberOfAnalysisResults,
            // NOTE: the order of the matchers is important because Stream could be used only once
            { _, c, r -> c.isNotEmpty() && c.contentEquals(r!!.toArray()) },
            { _, c, r -> c.isEmpty() && IntStream.empty().toArray().contentEquals(r!!.toArray()) },
        )
    }

    @Test
    fun testClosedStreamExample() {
        checkDiscoveredPropertiesWithExceptions(
            IntStreamExample::closedStreamExample,
            ignoreNumberOfAnalysisResults,
            { _, _, r -> r.isException<IllegalStateException>() },
        )
    }

    @Test
    fun testGenerateExample() {
        checkDiscoveredProperties(
            IntStreamExample::generateExample,
            ignoreNumberOfAnalysisResults,
            { _, r -> r!!.contentEquals(IntArray(10) { 42 }) }
        )
    }

    @Test
    fun testIterateExample() {
        checkDiscoveredProperties(
            IntStreamExample::iterateExample,
            ignoreNumberOfAnalysisResults,
            { _, r -> r!!.contentEquals(IntArray(10) { i -> 42 + i }) }
        )
    }

    @Test
    fun testConcatExample() {
        checkDiscoveredProperties(
            IntStreamExample::concatExample,
            ignoreNumberOfAnalysisResults,
            { _, r -> r!!.contentEquals(IntArray(10) { 42 } + IntArray(10) { i -> 42 + i }) }
        )
    }

    @Test
    fun testRangeExample() {
        checkDiscoveredProperties(
            IntStreamExample::rangeExample,
            ignoreNumberOfAnalysisResults,
            { _, r -> r!!.contentEquals(IntArray(10) { it }) }
        )
    }

    @Test
    fun testRangeClosedExample() {
        checkDiscoveredProperties(
            IntStreamExample::rangeClosedExample,
            ignoreNumberOfAnalysisResults,
            { _, r -> r!!.contentEquals(IntArray(11) { it }) }
        )
    }
}

private fun List<Short?>.ints(mapping: (Short?) -> Int = { it?.toInt() ?: 0 }): IntArray =
    map { mapping(it) }.toIntArray()
