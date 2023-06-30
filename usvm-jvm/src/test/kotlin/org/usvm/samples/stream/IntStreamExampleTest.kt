package org.usvm.samples.stream

import org.junit.jupiter.api.Disabled
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
    @Disabled("Expected exactly 1 executions, but 3 found")
    fun testReturningStreamAsParameterExample() {
        checkDiscoveredProperties(
            IntStreamExample::returningStreamAsParameterExample,
            eq(1),
            { _, s, r -> s != null && s.toList() == r!!.toList() },
        )
    }

    @Test
    @Disabled("Not implemented: virtual calls with abstract methods")
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
    @Disabled("Not implemented: virtual calls with abstract methods")
    fun testFilterExample() {
        checkDiscoveredProperties(
            IntStreamExample::filterExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> null !in c && r == false },
            { _, c, r -> null in c && r == true },
        )
    }

    @Test
    @Disabled("Not implemented: virtual calls with abstract methods")
    fun testMapExample() {
        checkDiscoveredProperties(
            IntStreamExample::mapExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> null in c && r.contentEquals(c.ints { it?.toInt()?.times(2) ?: 0 }) },
            { _, c: List<Short?>, r -> null !in c && r.contentEquals(c.ints { it?.toInt()?.times(2) ?: 0 }) },
        )
    }

    @Test
    @Disabled("Not implemented: virtual calls with abstract methods")
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
    @Disabled("Not implemented: virtual calls with abstract methods")
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
    @Disabled("Not implemented: virtual calls with abstract methods")
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
    @Disabled("Not implemented: virtual calls with abstract methods")
    fun testFlatMapExample() {
        checkDiscoveredProperties(
            IntStreamExample::flatMapExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r ->
                val intLists = c.mapNotNull {
                    it.toInt().let { i -> listOf(i, i) }
                }

                r != null && r.contentEquals(intLists.flatten().toIntArray())
            },
        )
    }

    @Test
    @Disabled("Not implemented: virtual calls with abstract methods")
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
    @Disabled("Not implemented: virtual calls with abstract methods")
    @Tag("slow")
    // TODO slow sorting https://github.com/UnitTestBot/UTBotJava/issues/188
    fun testSortedExample() {
        checkDiscoveredProperties(
            IntStreamExample::sortedExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.last() < c.first() && r != null && r.asSequence().isSorted() }
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
    @Disabled("Not implemented: virtual calls with abstract methods")
    fun testLimitExample() {
        checkDiscoveredProperties(
            IntStreamExample::limitExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.size <= 2 && c.ints().contentEquals(r) },
            { _, c, r -> c.size > 2 && c.take(2).ints().contentEquals(r) },
        )
    }

    @Test
    @Disabled("Not implemented: virtual calls with abstract methods")
    fun testSkipExample() {
        checkDiscoveredProperties(
            IntStreamExample::skipExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.size > 2 && c.drop(2).ints().contentEquals(r) },
            { _, c, r -> c.size <= 2 && r != null && r.isEmpty() },
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
    @Disabled("Not implemented: virtual calls with abstract methods")
    fun testToArrayExample() {
        checkDiscoveredProperties(
            IntStreamExample::toArrayExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.ints().contentEquals(r) },
        )
    }

    @Test
    @Disabled("Index 1 out of bounds for length 1")
    fun testReduceExample() {
        checkDiscoveredProperties(
            IntStreamExample::reduceExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r == 42 },
            { _, c: List<Short?>, r -> c.isNotEmpty() && r == c.filterNotNull().sum() + 42 },
        )
    }

    @Test
    @Disabled("Not implemented: virtual calls with abstract methods")
    fun testOptionalReduceExample() {
        checkDiscoveredPropertiesWithExceptions(
            IntStreamExample::optionalReduceExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r.getOrThrow() == OptionalInt.empty() },
            { _, c: List<Short?>, r -> c.isNotEmpty() && r.getOrThrow() == OptionalInt.of(c.filterNotNull().sum()) },
        )
    }

    @Test
    @Disabled("Index 1 out of bounds for length 1")
    fun testSumExample() {
        checkDiscoveredProperties(
            IntStreamExample::sumExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r == 0 },
            { _, c, r -> c.isNotEmpty() && c.filterNotNull().sum() == r },
        )
    }

    @Test
    @Disabled("Not implemented: virtual calls with abstract methods")
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
    @Disabled("Not implemented: virtual calls with abstract methods")
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
    @Disabled("Index 1 out of bounds for length 1")
    fun testCountExample() {
        checkDiscoveredProperties(
            IntStreamExample::countExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r == 0L },
            { _, c, r -> c.isNotEmpty() && c.size.toLong() == r },
        )
    }

    @Test
    @Disabled("Index 1 out of bounds for length 1")
    fun testAverageExample() {
        checkDiscoveredProperties(
            IntStreamExample::averageExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r == OptionalDouble.empty() },
            { _, c, r -> c.isNotEmpty() && c.mapNotNull { it.toInt() }.average() == r!!.asDouble },
        )
    }

    @Test
    @Disabled("Index 1 out of bounds for length 1")
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
    @Disabled("Index 1 out of bounds for length 1")
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
    @Disabled("Index 1 out of bounds for length 1")
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
    @Disabled("Index 1 out of bounds for length 1")
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
    @Disabled("Index 1 out of bounds for length 1")
    fun testFindFirstExample() {
        checkDiscoveredProperties(
            IntStreamExample::findFirstExample,
            eq(3),
            { _, c, r -> c.isEmpty() && r == OptionalInt.empty() },
            { _, c, r -> c.isNotEmpty() && r == OptionalInt.of(c.ints().first()) },
        )
    }

    @Test
    @Disabled("Not implemented: reference cast")
    fun testAsLongStreamExample() {
        checkDiscoveredProperties(
            IntStreamExample::asLongStreamExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.ints().map { it.toLong() }.toList() == r!!.toList() },
        )
    }

    @Test
    @Disabled("Not implemented: reference cast")
    fun testAsDoubleStreamExample() {
        checkDiscoveredProperties(
            IntStreamExample::asDoubleStreamExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.ints().map { it.toDouble() }.toList() == r!!.toList() },
        )
    }

    @Test
    @Disabled("Not implemented: reference cast")
    fun testBoxedExample() {
        checkDiscoveredProperties(
            IntStreamExample::boxedExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.ints().toList() == r!!.toList() },
        )
    }

    @Test
    @Disabled("Not implemented: reference cast")
    fun testIteratorExample() {
        checkDiscoveredProperties(
            IntStreamExample::iteratorSumExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r == 0 },
            { _, c: List<Short?>, r -> c.isNotEmpty() && c.ints().sum() == r },
        )
    }

    @Test
    @Disabled("Not implemented: Unexpected lvalue org.usvm.machine.JcStaticFieldRef")
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
    @Disabled("Not implemented: reference cast")
    fun testClosedStreamExample() {
        checkDiscoveredPropertiesWithExceptions(
            IntStreamExample::closedStreamExample,
            ignoreNumberOfAnalysisResults,
            { _, _, r -> r.isException<IllegalStateException>() },
        )
    }

    @Test
    @Disabled("Not implemented: reference cast")
    fun testGenerateExample() {
        checkDiscoveredProperties(
            IntStreamExample::generateExample,
            ignoreNumberOfAnalysisResults,
            { _, r -> r != null && r.contentEquals(IntArray(10) { 42 }) }
        )
    }

    @Test
    @Disabled("Index 1 out of bounds for length 1")
    fun testIterateExample() {
        checkDiscoveredProperties(
            IntStreamExample::iterateExample,
            ignoreNumberOfAnalysisResults,
            { _, r -> r != null && r.contentEquals(IntArray(10) { i -> 42 + i }) }
        )
    }

    @Test
    @Disabled("Not implemented: reference cast")
    fun testConcatExample() {
        checkDiscoveredProperties(
            IntStreamExample::concatExample,
            ignoreNumberOfAnalysisResults,
            { _, r -> r != null && r.contentEquals(IntArray(10) { 42 } + IntArray(10) { i -> 42 + i }) }
        )
    }

    @Test
    @Disabled("Not implemented: reference cast")
    fun testRangeExample() {
        checkDiscoveredProperties(
            IntStreamExample::rangeExample,
            ignoreNumberOfAnalysisResults,
            { _, r -> r != null && r.contentEquals(IntArray(10) { it }) }
        )
    }

    @Test
    @Disabled("Not implemented: reference cast")
    fun testRangeClosedExample() {
        checkDiscoveredProperties(
            IntStreamExample::rangeClosedExample,
            ignoreNumberOfAnalysisResults,
            { _, r -> r != null && r.contentEquals(IntArray(11) { it }) }
        )
    }
}

private fun List<Short?>.ints(mapping: (Short?) -> Int = { it?.toInt() ?: 0 }): IntArray =
    map { mapping(it) }.toIntArray()
