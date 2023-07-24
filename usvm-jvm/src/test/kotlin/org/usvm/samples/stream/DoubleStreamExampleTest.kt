package org.usvm.samples.stream

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.isException


import java.util.OptionalDouble
import java.util.stream.DoubleStream
import kotlin.streams.toList

@Disabled("Still too complex")
class DoubleStreamExampleTest : JavaMethodTestRunner() {
    @Test
    fun testReturningStreamAsParameterExample() {
        checkDiscoveredProperties(
            DoubleStreamExample::returningStreamAsParameterExample,
            eq(1),
            { _, s, r -> s != null && s.toList() == r!!.toList() },
        )
    }

    @Test
    fun testUseParameterStream() {
        checkDiscoveredProperties(
            DoubleStreamExample::useParameterStream,
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
            DoubleStreamExample::filterExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> null !in c && r == false },
            { _, c, r -> null in c && r == true },
        )
    }

    @Test
    fun testMapExample() {
        checkDiscoveredProperties(
            DoubleStreamExample::mapExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> null in c && r.contentEquals(c.doubles { it?.toDouble()?.times(2) ?: 0.0 }) },
            { _, c: List<Short?>, r -> null !in c && r.contentEquals(c.doubles { it?.toDouble()?.times(2) ?: 0.0 }) },
        )
    }

    @Test
    fun testMapToObjExample() {
        checkDiscoveredProperties(
            DoubleStreamExample::mapToObjExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r ->
                val intArrays = c.doubles().map { it.let { i -> doubleArrayOf(i, i) } }.toTypedArray()

                null in c && intArrays.zip(r as Array<out Any>)
                    .all { it.first.contentEquals(it.second as DoubleArray?) }
            },
            { _, c: List<Short?>, r ->
                val intArrays = c.doubles().map { it.let { i -> doubleArrayOf(i, i) } }.toTypedArray()

                null !in c && intArrays.zip(r as Array<out Any>)
                    .all { it.first.contentEquals(it.second as DoubleArray?) }
            },
        )
    }

    @Test
    fun testMapToIntExample() {
        checkDiscoveredProperties(
            DoubleStreamExample::mapToIntExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r ->
                val ints = c.doubles().map { it.toInt() }.toIntArray()

                null in c && ints.contentEquals(r)
            },
            { _, c: List<Short?>, r ->
                val ints = c.doubles().map { it.toInt() }.toIntArray()

                null !in c && ints.contentEquals(r)
            },
        )
    }

    @Test
    fun testMapToLongExample() {
        checkDiscoveredProperties(
            DoubleStreamExample::mapToLongExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r ->
                val longs = c.doubles().map { it.toLong() }.toLongArray()

                null in c && longs.contentEquals(r)
            },
            { _, c: List<Short?>, r ->
                val longs = c.doubles().map { it.toLong() }.toLongArray()

                null !in c && longs.contentEquals(r)
            },
        )
    }

    @Test
    fun testFlatMapExample() {
        checkDiscoveredProperties(
            DoubleStreamExample::flatMapExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r ->
                val intLists = c.mapNotNull {
                    it.toDouble().let { i -> listOf(i, i) }
                }

                r != null && r.contentEquals(intLists.flatten().toDoubleArray())
            },
        )
    }

    @Test
    fun testDistinctExample() {
        checkDiscoveredProperties(
            DoubleStreamExample::distinctExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r ->
                val doubles = c.doubles()

                doubles.contentEquals(doubles.distinct().toDoubleArray()) && r == false
            },
            { _, c, r ->
                val doubles = c.doubles()

                !doubles.contentEquals(doubles.distinct().toDoubleArray()) && r == true
            },
        )
    }

    @Test
    @Tag("slow")
    @Disabled("Not implemented: virtual calls with abstract methods")
    fun testSortedExample() {
        checkDiscoveredProperties(
            DoubleStreamExample::sortedExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.last() < c.first() && r != null && r.asSequence().isSorted() }
        )
    }

    // TODO unsupported
    /*
        @Test
        fun testPeekExample() {
            checkThisAndStaticsAfter(
                DoubleStreamExample::peekExample,
                ignoreNumberOfAnalysisResults,
                *streamConsumerStaticsMatchers,
            )
        }
    */

    @Test
    fun testLimitExample() {
        checkDiscoveredProperties(
            DoubleStreamExample::limitExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.size <= 2 && c.doubles().contentEquals(r) },
            { _, c, r -> c.size > 2 && c.take(2).doubles().contentEquals(r) },
        )
    }

    @Test
    fun testSkipExample() {
        checkDiscoveredProperties(
            DoubleStreamExample::skipExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.size > 2 && c.drop(2).doubles().contentEquals(r) },
            { _, c, r -> c.size <= 2 && r != null && r.isEmpty() },
        )
    }

    // TODO unsupported
//    @Test
//    fun testForEachExample() {
//        checkThisAndStaticsAfter(
//            DoubleStreamExample::forEachExample,
//            ignoreNumberOfAnalysisResults,
//            *streamConsumerStaticsMatchers,
//        )
//    }

    @Test
    fun testToArrayExample() {
        checkDiscoveredProperties(
            DoubleStreamExample::toArrayExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.doubles().contentEquals(r) },
        )
    }

    @Test
    fun testReduceExample() {
        checkDiscoveredProperties(
            DoubleStreamExample::reduceExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r == 42.0 },
            { _, c: List<Short?>, r -> c.isNotEmpty() && r == c.filterNotNull().sum() + 42.0 },
        )
    }

    @Test
    fun testOptionalReduceExample() {
        checkDiscoveredPropertiesWithExceptions(
            DoubleStreamExample::optionalReduceExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r.getOrThrow() == OptionalDouble.empty() },
            { _, c: List<Short?>, r ->
                c.isNotEmpty() && r.getOrThrow() == OptionalDouble.of(
                    c.filterNotNull().sum().toDouble()
                )
            },
        )
    }

    @Test
    fun testSumExample() {
        checkDiscoveredProperties(
            DoubleStreamExample::sumExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r == 0.0 },
            { _, c, r -> c.isNotEmpty() && c.filterNotNull().sum().toDouble() == r },
        )
    }

    @Test
    fun testMinExample() {
        checkDiscoveredPropertiesWithExceptions(
            DoubleStreamExample::minExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r.getOrThrow() == OptionalDouble.empty() },
            { _, c, r ->
                c.isNotEmpty() && r.getOrThrow() == OptionalDouble.of(c.mapNotNull { it.toDouble() }.minOrNull()!!)
            },
        )
    }

    @Test
    fun testMaxExample() {
        checkDiscoveredPropertiesWithExceptions(
            DoubleStreamExample::maxExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r.getOrThrow() == OptionalDouble.empty() },
            { _, c, r ->
                c.isNotEmpty() && r.getOrThrow() == OptionalDouble.of(c.mapNotNull { it.toDouble() }.maxOrNull()!!)
            },
        )
    }

    @Test
    fun testCountExample() {
        checkDiscoveredProperties(
            DoubleStreamExample::countExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r == 0L },
            { _, c, r -> c.isNotEmpty() && c.size.toLong() == r },
        )
    }

    @Test
    fun testAverageExample() {
        checkDiscoveredProperties(
            DoubleStreamExample::averageExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r == OptionalDouble.empty() },
            { _, c, r -> c.isNotEmpty() && c.mapNotNull { it.toDouble() }.average() == r!!.asDouble },
        )
    }

    @Test
    fun testSummaryStatisticsExample() {
        checkDiscoveredProperties(
            DoubleStreamExample::summaryStatisticsExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r ->
                val sum = r!!.sum
                val count = r.count
                val min = r.min
                val max = r.max

                val allStatisticsAreCorrect = sum == 0.0 &&
                        count == 0L &&
                        min == Double.POSITIVE_INFINITY &&
                        max == Double.NEGATIVE_INFINITY

                c.isEmpty() && allStatisticsAreCorrect
            },
            { _, c, r ->
                val sum = r!!.sum
                val count = r.count
                val min = r.min
                val max = r.max

                val doubles = c.doubles()

                val allStatisticsAreCorrect = sum == doubles.sum() &&
                        count == doubles.size.toLong() &&
                        min == doubles.minOrNull() &&
                        max == doubles.maxOrNull()

                c.isNotEmpty() && allStatisticsAreCorrect
            },
        )
    }

    @Test
    fun testAnyMatchExample() {
        // TODO exceeds even default step limit 3500 => too slow
        checkDiscoveredProperties(
            DoubleStreamExample::anyMatchExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r == false },
            { _, c, r -> c.isNotEmpty() && c.doubles().all { it == 0.0 } && r == false },
            { _, c, r ->
                val doubles = c.doubles()

                c.isNotEmpty() && doubles.first() != 0.0 && doubles.last() == 0.0 && r == true
            },
            { _, c, r ->
                val doubles = c.doubles()

                c.isNotEmpty() && doubles.first() == 0.0 && doubles.last() != 0.0 && r == true
            },
            { _, c, r ->
                val doubles = c.doubles()

                c.isNotEmpty() && doubles.none { it == 0.0 } && r == true
            },
        )
    }

    @Test
    fun testAllMatchExample() {
        checkDiscoveredProperties(
            DoubleStreamExample::allMatchExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r == true },
            { _, c, r -> c.isNotEmpty() && c.doubles().all { it == 0.0 } && r == false },
            { _, c, r ->
                val doubles = c.doubles()

                c.isNotEmpty() && doubles.first() != 0.0 && doubles.last() == 0.0 && r == false
            },
            { _, c, r ->
                val doubles = c.doubles()

                c.isNotEmpty() && doubles.first() == 0.0 && doubles.last() != 0.0 && r == false
            },
            { _, c, r ->
                val doubles = c.doubles()

                c.isNotEmpty() && doubles.none { it == 0.0 } && r == true
            },
        )
    }

    @Test
    fun testNoneMatchExample() {
        checkDiscoveredProperties(
            DoubleStreamExample::noneMatchExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r == true },
            { _, c, r -> c.isNotEmpty() && c.doubles().all { it == 0.0 } && r == true },
            { _, c, r ->
                val doubles = c.doubles()

                c.isNotEmpty() && doubles.first() != 0.0 && doubles.last() == 0.0 && r == false
            },
            { _, c, r ->
                val doubles = c.doubles()

                c.isNotEmpty() && doubles.first() == 0.0 && doubles.last() != 0.0 && r == false
            },
            { _, c, r ->
                val doubles = c.doubles()

                c.isNotEmpty() && doubles.none { it == 0.0 } && r == false
            },
        )
    }

    @Test
    fun testFindFirstExample() {
        checkDiscoveredProperties(
            DoubleStreamExample::findFirstExample,
            eq(3),
            { _, c, r -> c.isEmpty() && r == OptionalDouble.empty() },
            { _, c, r -> c.isNotEmpty() && r == OptionalDouble.of(c.doubles().first()) },
        )
    }

    @Test
    fun testBoxedExample() {
        checkDiscoveredProperties(
            DoubleStreamExample::boxedExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.doubles().toList() == r!!.toList() },
        )
    }

    @Test
    fun testIteratorExample() {
        checkDiscoveredProperties(
            DoubleStreamExample::iteratorSumExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r == 0.0 },
            { _, c: List<Short?>, r -> c.isNotEmpty() && c.doubles().sum() == r },
        )
    }

    @Test
    @Disabled("Unexpected lvalue org.usvm.machine.JcStaticFieldRef@3f95a1b3")
    fun testStreamOfExample() {
        checkDiscoveredProperties(
            DoubleStreamExample::streamOfExample,
            ignoreNumberOfAnalysisResults,
            // NOTE: the order of the matchers is important because Stream could be used only once
            { _, c, r -> c.isNotEmpty() && c.contentEquals(r!!.toArray()) },
            { _, c, r -> c.isEmpty() && DoubleStream.empty().toArray().contentEquals(r!!.toArray()) },
        )
    }

    @Test
    fun testClosedStreamExample() {
        checkDiscoveredPropertiesWithExceptions(
            DoubleStreamExample::closedStreamExample,
            ignoreNumberOfAnalysisResults,
            { _, _, r -> r.isException<IllegalStateException>() },
        )
    }

    @Test
    fun testGenerateExample() {
        checkDiscoveredProperties(
            DoubleStreamExample::generateExample,
            ignoreNumberOfAnalysisResults,
            { _, r -> r != null && r.contentEquals(DoubleArray(10) { 42.0 }) }
        )
    }

    @Test
    fun testIterateExample() {
        checkDiscoveredProperties(
            DoubleStreamExample::iterateExample,
            ignoreNumberOfAnalysisResults,
            { _, r -> r != null && r.contentEquals(DoubleArray(10) { i -> 42.0 + i }) }
        )
    }

    @Test
    fun testConcatExample() {
        checkDiscoveredProperties(
            DoubleStreamExample::concatExample,
            ignoreNumberOfAnalysisResults,
            { _, r -> r != null && r.contentEquals(DoubleArray(10) { 42.0 } + DoubleArray(10) { i -> 42.0 + i }) }
        )
    }
}

private fun List<Short?>.doubles(mapping: (Short?) -> Double = { it?.toDouble() ?: 0.0 }): DoubleArray =
    map { mapping(it) }.toDoubleArray()
