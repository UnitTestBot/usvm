package org.usvm.samples.stream

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.disableTest
import org.usvm.util.isException
import java.util.*
import java.util.stream.LongStream
import kotlin.streams.toList

class LongStreamExampleTest : JavaMethodTestRunner() {
    @Test
    fun testReturningStreamAsParameterExample() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredProperties(
            LongStreamExample::returningStreamAsParameterExample,
            eq(1),
            { _, s, r -> s != null && s.toList() == r!!.toList() },
        )
    }

    @Test
    fun testUseParameterStream() = disableTest("Index 1 out of bounds for length 1 | URegistersStack.writeRegister") {
        checkDiscoveredProperties(
            LongStreamExample::useParameterStream,
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
    fun testFilterExample() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
        checkDiscoveredProperties(
            LongStreamExample::filterExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> null !in c && r == false },
            { _, c, r -> null in c && r == true },
        )
    }

    @Test
    fun testMapExample() = disableTest("Index 1 out of bounds for length 1 | URegistersStack.writeRegister") {
        checkDiscoveredProperties(
            LongStreamExample::mapExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> null in c && r.contentEquals(c.longs { it?.toLong()?.times(2) ?: 0L }) },
            { _, c: List<Short?>, r -> null !in c && r.contentEquals(c.longs { it?.toLong()?.times(2) ?: 0L }) },
        )
    }

    @Test
    fun testMapToObjExample() = disableTest("Index 1 out of bounds for length 1 | URegistersStack.writeRegister") {
        checkDiscoveredProperties(
            LongStreamExample::mapToObjExample,
            ignoreNumberOfAnalysisResults,
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
    fun testMapToIntExample() = disableTest("Index 1 out of bounds for length 1 | URegistersStack.writeRegister") {
        checkDiscoveredProperties(
            LongStreamExample::mapToIntExample,
            ignoreNumberOfAnalysisResults,
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
    fun testMapToDoubleExample() = disableTest("Index 1 out of bounds for length 1 | URegistersStack.writeRegister") {
        checkDiscoveredProperties(
            LongStreamExample::mapToDoubleExample,
            ignoreNumberOfAnalysisResults,
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
    fun testFlatMapExample() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredProperties(
            LongStreamExample::flatMapExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r ->
                val intLists = c.map {
                    (it?.toLong() ?: 0L).let { i -> listOf(i, i) }
                }

                r != null && r.contentEquals(intLists.flatten().toLongArray())
            },
        )
    }

    @Test
    fun testDistinctExample() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
        checkDiscoveredProperties(
            LongStreamExample::distinctExample,
            ignoreNumberOfAnalysisResults,
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
    fun testSortedExample() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredProperties(
            LongStreamExample::sortedExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.last() < c.first() && r != null && r.asSequence().isSorted() }
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
    fun testLimitExample() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
        checkDiscoveredProperties(
            LongStreamExample::limitExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.size <= 2 && c.longs().contentEquals(r) },
            { _, c, r -> c.size > 2 && c.take(2).longs().contentEquals(r) },
        )
    }

    @Test
    fun testSkipExample() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
        checkDiscoveredProperties(
            LongStreamExample::skipExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.size > 2 && c.drop(2).longs().contentEquals(r) },
            { _, c, r -> c.size <= 2 && r != null && r.isEmpty() },
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
    fun testToArrayExample() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredProperties(
            LongStreamExample::toArrayExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.longs().contentEquals(r) },
        )
    }

    @Test
    fun testReduceExample() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
        checkDiscoveredProperties(
            LongStreamExample::reduceExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r == 42L },
            { _, c: List<Short?>, r -> c.isNotEmpty() && r == c.filterNotNull().sum() + 42L },
        )
    }

    @Test
    fun testOptionalReduceExample() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
        checkDiscoveredPropertiesWithExceptions(
            LongStreamExample::optionalReduceExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r.getOrThrow() == OptionalLong.empty() },
            { _, c: List<Short?>, r ->
                c.isNotEmpty() && r.getOrThrow() == OptionalLong.of(
                    c.filterNotNull().sum().toLong()
                )
            },
        )
    }

    @Test
    fun testSumExample() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
        checkDiscoveredProperties(
            LongStreamExample::sumExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r == 0L },
            { _, c, r -> c.isNotEmpty() && c.filterNotNull().sum().toLong() == r },
        )
    }

    @Test
    fun testMinExample() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
        checkDiscoveredPropertiesWithExceptions(
            LongStreamExample::minExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r.getOrThrow() == OptionalLong.empty() },
            { _, c, r ->
                c.isNotEmpty() && r.getOrThrow() == OptionalLong.of(c.mapNotNull { it.toLong() }.minOrNull()!!)
            },
        )
    }

    @Test
    fun testMaxExample() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
        checkDiscoveredPropertiesWithExceptions(
            LongStreamExample::maxExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r.getOrThrow() == OptionalLong.empty() },
            { _, c, r ->
                c.isNotEmpty() && r.getOrThrow() == OptionalLong.of(c.mapNotNull { it.toLong() }.maxOrNull()!!)
            },
        )
    }

    @Test

    fun testCountExample() = disableTest("Index 1 out of bounds for length 1") {
        checkDiscoveredProperties(
            LongStreamExample::countExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r == 0L },
            { _, c, r -> c.isNotEmpty() && c.size.toLong() == r },
        )
    }

    @Test
    fun testAverageExample() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
        checkDiscoveredProperties(
            LongStreamExample::averageExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r == OptionalDouble.empty() },
            { _, c, r -> c.isNotEmpty() && c.mapNotNull { it.toLong() }.average() == r!!.asDouble },
        )
    }

    @Test
    fun testSummaryStatisticsExample() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
        checkDiscoveredProperties(
            LongStreamExample::summaryStatisticsExample,
            ignoreNumberOfAnalysisResults,
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
    fun testAnyMatchExample() = disableTest("Index 1 out of bounds for length 1 | URegistersStack.writeRegister") {
        checkDiscoveredProperties(
            LongStreamExample::anyMatchExample,
            ignoreNumberOfAnalysisResults,
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
    fun testAllMatchExample() = disableTest("Index 1 out of bounds for length 1 | URegistersStack.writeRegister") {
        checkDiscoveredProperties(
            LongStreamExample::allMatchExample,
            ignoreNumberOfAnalysisResults,
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
    fun testNoneMatchExample() = disableTest("Index 1 out of bounds for length 1 | URegistersStack.writeRegister") {
        checkDiscoveredProperties(
            LongStreamExample::noneMatchExample,
            ignoreNumberOfAnalysisResults,
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
    fun testFindFirstExample() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
        checkDiscoveredProperties(
            LongStreamExample::findFirstExample,
            eq(3),
            { _, c, r -> c.isEmpty() && r == OptionalLong.empty() },
            { _, c, r -> c.isNotEmpty() && r == OptionalLong.of(c.longs().first()) },
        )
    }

    @Test
    fun testAsDoubleStreamExample() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredProperties(
            LongStreamExample::asDoubleStreamExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.longs().map { it.toDouble() }.toList() == r!!.toList() },
        )
    }

    @Test
    fun testBoxedExample() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredProperties(
            LongStreamExample::boxedExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.longs().toList() == r!!.toList() },
        )
    }

    @Test
    fun testIteratorExample() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
        checkDiscoveredProperties(
            LongStreamExample::iteratorSumExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r == 0L },
            { _, c: List<Short?>, r -> c.isNotEmpty() && c.longs().sum() == r },
        )
    }

    @Test
    fun testStreamOfExample() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
        checkDiscoveredProperties(
            LongStreamExample::streamOfExample,
            ignoreNumberOfAnalysisResults,
            // NOTE: the order of the matchers is important because Stream could be used only once
            { _, c, r -> c.isNotEmpty() && c.contentEquals(r!!.toArray()) },
            { _, c, r -> c.isEmpty() && LongStream.empty().toArray().contentEquals(r!!.toArray()) },
        )
    }

    @Test
    fun testClosedStreamExample() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredPropertiesWithExceptions(
            LongStreamExample::closedStreamExample,
            ignoreNumberOfAnalysisResults,
            { _, _, r -> r.isException<IllegalStateException>() },
        )
    }

    @Test
    fun testGenerateExample() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredProperties(
            LongStreamExample::generateExample,
            ignoreNumberOfAnalysisResults,
            { _, r -> r != null && r.contentEquals(LongArray(10) { 42L }) }
        )
    }

    @Test
    fun testIterateExample() = disableTest("Index 1 out of bounds for length 1 | URegistersStack.writeRegister") {
        checkDiscoveredProperties(
            LongStreamExample::iterateExample,
            ignoreNumberOfAnalysisResults,
            { _, r -> r != null && r.contentEquals(LongArray(10) { i -> 42L + i }) }
        )
    }

    @Test
    fun testConcatExample() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredProperties(
            LongStreamExample::concatExample,
            ignoreNumberOfAnalysisResults,
            { _, r -> r != null && r.contentEquals(LongArray(10) { 42L } + LongArray(10) { i -> 42L + i }) }
        )
    }

    @Test
    fun testRangeExample() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredProperties(
            LongStreamExample::rangeExample,
            ignoreNumberOfAnalysisResults,
            { _, r -> r != null && r.contentEquals(LongArray(10) { it.toLong() }) }
        )
    }

    @Test
    fun testRangeClosedExample() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredProperties(
            LongStreamExample::rangeClosedExample,
            ignoreNumberOfAnalysisResults,
            { _, r -> r != null && r.contentEquals(LongArray(11) { it.toLong() }) }
        )
    }
}

private fun List<Short?>.longs(mapping: (Short?) -> Long = { it?.toLong() ?: 0L }): LongArray =
    map { mapping(it) }.toLongArray()
