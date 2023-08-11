package org.usvm.samples.stream

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.disableTest
import org.usvm.util.isException
import java.util.*
import java.util.stream.DoubleStream
import kotlin.streams.toList

class DoubleStreamExampleTest : JavaMethodTestRunner() {
    @Test
    fun testReturningStreamAsParameterExample() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredProperties(
            DoubleStreamExample::returningStreamAsParameterExample,
            eq(1),
            { _, s, r -> s != null && s.toList() == r!!.toList() },
        )
    }

    @Test
    fun testUseParameterStream() = disableTest("Index 1 out of bounds for length 1 | URegistersStack.writeRegister") {
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
    fun testFilterExample() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
        checkDiscoveredProperties(
            DoubleStreamExample::filterExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> null !in c && r == false },
            { _, c, r -> null in c && r == true },
        )
    }

    @Test
    fun testMapExample() = disableTest("Index 1 out of bounds for length 1 | URegistersStack.writeRegister") {
        checkDiscoveredProperties(
            DoubleStreamExample::mapExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> null in c && r.contentEquals(c.doubles { it?.toDouble()?.times(2) ?: 0.0 }) },
            { _, c: List<Short?>, r -> null !in c && r.contentEquals(c.doubles { it?.toDouble()?.times(2) ?: 0.0 }) },
        )
    }

    @Test
    fun testMapToObjExample() = disableTest("Index 1 out of bounds for length 1 | URegistersStack.writeRegister") {
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
    fun testMapToIntExample() = disableTest("Index 1 out of bounds for length 1 | URegistersStack.writeRegister") {
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
    fun testMapToLongExample() = disableTest("Index 1 out of bounds for length 1 | URegistersStack.writeRegister") {
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
    fun testFlatMapExample() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
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
    fun testDistinctExample() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
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
    fun testSortedExample() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredProperties(
            DoubleStreamExample::sortedExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.last() < c.first() && r != null && r.asSequence().isSorted() }
        )
    }

    // TODO unsupported
    /*
        @Test
        fun testPeekExample() = disableTest("") {
            checkThisAndStaticsAfter(
                DoubleStreamExample::peekExample,
                ignoreNumberOfAnalysisResults,
                *streamConsumerStaticsMatchers,
            )
        }
    */

    @Test
    fun testLimitExample() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
        checkDiscoveredProperties(
            DoubleStreamExample::limitExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.size <= 2 && c.doubles().contentEquals(r) },
            { _, c, r -> c.size > 2 && c.take(2).doubles().contentEquals(r) },
        )
    }

    @Test
    fun testSkipExample() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
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
    fun testToArrayExample() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredProperties(
            DoubleStreamExample::toArrayExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.doubles().contentEquals(r) },
        )
    }

    @Test
    fun testReduceExample() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
        checkDiscoveredProperties(
            DoubleStreamExample::reduceExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r == 42.0 },
            { _, c: List<Short?>, r -> c.isNotEmpty() && r == c.filterNotNull().sum() + 42.0 },
        )
    }

    @Test
    fun testOptionalReduceExample() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
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
    fun testSumExample() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
        checkDiscoveredProperties(
            DoubleStreamExample::sumExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r == 0.0 },
            { _, c, r -> c.isNotEmpty() && c.filterNotNull().sum().toDouble() == r },
        )
    }

    @Test
    fun testMinExample() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
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
    fun testMaxExample() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
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
    fun testCountExample() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
        checkDiscoveredProperties(
            DoubleStreamExample::countExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r == 0L },
            { _, c, r -> c.isNotEmpty() && c.size.toLong() == r },
        )
    }

    @Test
    fun testAverageExample() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
        checkDiscoveredProperties(
            DoubleStreamExample::averageExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r == OptionalDouble.empty() },
            { _, c, r -> c.isNotEmpty() && c.mapNotNull { it.toDouble() }.average() == r!!.asDouble },
        )
    }

    @Test
    fun testSummaryStatisticsExample() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
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
    fun testAnyMatchExample() = disableTest("Index 1 out of bounds for length 1 | URegistersStack.writeRegister") {
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
    fun testAllMatchExample() = disableTest("Index 1 out of bounds for length 1 | URegistersStack.writeRegister") {
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
    fun testNoneMatchExample() = disableTest("Index 1 out of bounds for length 1 | URegistersStack.writeRegister") {
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
    fun testFindFirstExample() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
        checkDiscoveredProperties(
            DoubleStreamExample::findFirstExample,
            eq(3),
            { _, c, r -> c.isEmpty() && r == OptionalDouble.empty() },
            { _, c, r -> c.isNotEmpty() && r == OptionalDouble.of(c.doubles().first()) },
        )
    }

    @Test
    fun testBoxedExample() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredProperties(
            DoubleStreamExample::boxedExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.doubles().toList() == r!!.toList() },
        )
    }

    @Test
    fun testIteratorExample() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
        checkDiscoveredProperties(
            DoubleStreamExample::iteratorSumExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r == 0.0 },
            { _, c: List<Short?>, r -> c.isNotEmpty() && c.doubles().sum() == r },
        )
    }

    @Test
    fun testStreamOfExample() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
        checkDiscoveredProperties(
            DoubleStreamExample::streamOfExample,
            ignoreNumberOfAnalysisResults,
            // NOTE: the order of the matchers is important because Stream could be used only once
            { _, c, r -> c.isNotEmpty() && c.contentEquals(r!!.toArray()) },
            { _, c, r -> c.isEmpty() && DoubleStream.empty().toArray().contentEquals(r!!.toArray()) },
        )
    }

    @Test
    fun testClosedStreamExample() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredPropertiesWithExceptions(
            DoubleStreamExample::closedStreamExample,
            ignoreNumberOfAnalysisResults,
            { _, _, r -> r.isException<IllegalStateException>() },
        )
    }

    @Test
    fun testGenerateExample() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredProperties(
            DoubleStreamExample::generateExample,
            ignoreNumberOfAnalysisResults,
            { _, r -> r != null && r.contentEquals(DoubleArray(10) { 42.0 }) }
        )
    }

    @Test
    fun testIterateExample() = disableTest("Index 1 out of bounds for length 1 | URegistersStack.writeRegister") {
        checkDiscoveredProperties(
            DoubleStreamExample::iterateExample,
            ignoreNumberOfAnalysisResults,
            { _, r -> r != null && r.contentEquals(DoubleArray(10) { i -> 42.0 + i }) }
        )
    }

    @Test
    fun testConcatExample() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredProperties(
            DoubleStreamExample::concatExample,
            ignoreNumberOfAnalysisResults,
            { _, r -> r != null && r.contentEquals(DoubleArray(10) { 42.0 } + DoubleArray(10) { i -> 42.0 + i }) }
        )
    }
}

private fun List<Short?>.doubles(mapping: (Short?) -> Double = { it?.toDouble() ?: 0.0 }): DoubleArray =
    map { mapping(it) }.toDoubleArray()
