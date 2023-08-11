package org.usvm.samples.stream

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.disableTest
import org.usvm.util.isException
import java.util.*
import java.util.stream.Collectors
import java.util.stream.Stream

class BaseStreamExampleTest : JavaMethodTestRunner() {
    @Test
    fun testReturningStreamAsParameterExample() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredProperties(
            BaseStreamExample::returningStreamAsParameterExample,
            eq(1),
            { _, s, r -> s != null && s.asList() == r!!.asList() },
        )
    }

    @Test
    fun testFilterExample() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
        checkDiscoveredProperties(
            BaseStreamExample::filterExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> null !in c && r == false },
            { _, c, r -> null in c && r == true },
        )
    }

    @Test
    fun testMapExample() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
        checkDiscoveredPropertiesWithExceptions(
            BaseStreamExample::mapExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> null in c && r.isException<NullPointerException>() },
            { _, c, r -> r.getOrThrow().contentEquals(c.map { it * 2 }.toTypedArray()) },
        )
    }

    @Test
    @Tag("slow")
    fun testMapToIntExample() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
        checkDiscoveredPropertiesWithExceptions(
            BaseStreamExample::mapToIntExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> null in c && r.isException<NullPointerException>() },
            { _, c, r -> r.getOrThrow().contentEquals(c.map { it.toInt() }.toIntArray()) },
        )
    }

    @Test
    @Tag("slow")
    fun testMapToLongExample() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
        checkDiscoveredPropertiesWithExceptions(
            BaseStreamExample::mapToLongExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> null in c && r.isException<NullPointerException>() },
            { _, c, r -> r.getOrThrow().contentEquals(c.map { it.toLong() }.toLongArray()) }
        )
    }

    @Test
    @Tag("slow")
    fun testMapToDoubleExample() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
        checkDiscoveredPropertiesWithExceptions(
            BaseStreamExample::mapToDoubleExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> null in c && r.isException<NullPointerException>() },
            { _, c, r -> r.getOrThrow().contentEquals(c.map { it.toDouble() }.toDoubleArray()) }
        )
    }

    @Test
    fun testFlatMapExample() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredProperties(
            BaseStreamExample::flatMapExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> r.contentEquals(c.flatMap { listOf(it, it) }.toTypedArray()) },
        )
    }

    @Test
    @Tag("slow")
    fun testFlatMapToIntExample() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredProperties(
            BaseStreamExample::flatMapToIntExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> r.contentEquals(c.flatMap { listOf(it?.toInt() ?: 0, it?.toInt() ?: 0) }.toIntArray()) },
        )
    }

    @Test
    fun testFlatMapToLongExample() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredProperties(
            BaseStreamExample::flatMapToLongExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> r.contentEquals(c.flatMap { listOf(it?.toLong() ?: 0L, it?.toLong() ?: 0L) }.toLongArray()) },
        )
    }

    @Test
    fun testFlatMapToDoubleExample() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredProperties(
            BaseStreamExample::flatMapToDoubleExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r ->
                r.contentEquals(c.flatMap { listOf(it?.toDouble() ?: 0.0, it?.toDouble() ?: 0.0) }.toDoubleArray())
            },
        )
    }

    @Test
    @Tag("slow")
    fun testDistinctExample() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
        checkDiscoveredProperties(
            BaseStreamExample::distinctExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c == c.distinct() && r == false },
            { _, c, r -> c != c.distinct() && r == true },
        )
    }

    @Test
    @Tag("slow")
    fun testSortedExample() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredProperties(
            BaseStreamExample::sortedExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.last() < c.first() && r != null && r.asSequence().isSorted() }
        )
    }

    // TODO unsupported
//    @Test
//    fun testPeekExample() {
//        checkThisAndStaticsAfter(
//            BaseStreamExample::peekExample,
//            ignoreNumberOfAnalysisResults,
//            *streamConsumerStaticsMatchers,
//        )
//    }

    @Test
    fun testLimitExample() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
        checkDiscoveredProperties(
            BaseStreamExample::limitExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.size <= 5 && c.toTypedArray().contentEquals(r) },
            { _, c, r -> c.size > 5 && c.take(5).toTypedArray().contentEquals(r) },
        )
    }

    @Test
    fun testSkipExample() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
        checkDiscoveredProperties(
            BaseStreamExample::skipExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.size > 5 && c.drop(5).toTypedArray().contentEquals(r) },
            { _, c, r -> c.size <= 5 && r != null && r.isEmpty() },
        )
    }

    // TODO unsupported
//    @Test
//    fun testForEachExample() = disableTest("") {
//        checkThisAndStaticsAfter(
//            BaseStreamExample::forEachExample,
//            ignoreNumberOfAnalysisResults,
//            *streamConsumerStaticsMatchers,
//        )
//    }

    @Test
    fun testToArrayExample() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredProperties(
            BaseStreamExample::toArrayExample,
            eq(2),
            { _, c, r -> c.toTypedArray().contentEquals(r) },
        )
    }

    @Test
    fun testReduceExample() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
        checkDiscoveredProperties(
            BaseStreamExample::reduceExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r == 42 },
            { _, c, r -> c.isNotEmpty() && r == c.sum() + 42 },
        )
    }

    @Test
    fun testOptionalReduceExample() = disableTest("Some properties were not discovered at positions (from 0): [0, 1, 2]") {
        checkDiscoveredPropertiesWithExceptions(
            BaseStreamExample::optionalReduceExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r.getOrThrow() == Optional.empty<Int>() },
            { _, c, r -> c.isNotEmpty() && c.single() == null && r.isException<NullPointerException>() },
            { _, c, r -> c.isNotEmpty() && r.getOrThrow() == Optional.of<Int>(c.sum()) },
        )
    }

    @Test
    fun testComplexReduceExample() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
        checkDiscoveredProperties(
            BaseStreamExample::complexReduceExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && c.sumOf { it.toDouble() } + 42.0 == r },
            { _, c: List<Int?>, r -> c.isNotEmpty() && c.sumOf { it?.toDouble() ?: 0.0 } + 42.0 == r },
        )
    }

    @Test
    fun testCollectorExample() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredProperties(
            BaseStreamExample::collectorExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.toSet() == r },
        )
    }

    @Test
    fun testCollectExample() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
        checkDiscoveredPropertiesWithExceptions(
            BaseStreamExample::collectExample,
            eq(2), // 3 executions instead of 2 expected
            { _, c, r -> null in c && r.isException<NullPointerException>() },
            { _, c, r -> null !in c && c.sum() == r.getOrThrow() },
        )
    }

    @Test
    fun testMinExample() = disableTest("Some properties were not discovered at positions (from 0): [0, 1, 2]") {
        checkDiscoveredPropertiesWithExceptions(
            BaseStreamExample::minExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r.getOrThrow() == Optional.empty<Int>() },
            { _, c, r -> c.isNotEmpty() && c.all { it == null } && r.isException<NullPointerException>() },
            { _, c, r -> c.isNotEmpty() && r.getOrThrow() == Optional.of<Int>(c.minOrNull()!!) },
        )
    }

    @Test
    fun testMaxExample() = disableTest("Some properties were not discovered at positions (from 0): [0, 1, 2]") {
        checkDiscoveredPropertiesWithExceptions(
            BaseStreamExample::maxExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r.getOrThrow() == Optional.empty<Int>() },
            { _, c, r -> c.isNotEmpty() && c.all { it == null } && r.isException<NullPointerException>() },
            { _, c, r -> c.isNotEmpty() && r.getOrThrow() == Optional.of<Int>(c.maxOrNull()!!) },
        )
    }

    @Test
    fun testCountExample() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
        checkDiscoveredProperties(
            BaseStreamExample::countExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r == 0L },
            { _, c, r -> c.isNotEmpty() && c.size.toLong() == r },
        )
    }

    @Test
    fun testAnyMatchExample() = disableTest("Index 1 out of bounds for length 1 | URegistersStack.writeRegister") {
        checkDiscoveredProperties(
            BaseStreamExample::anyMatchExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r == false },
            { _, c, r -> c.isNotEmpty() && c.all { it == null } && r == true },
            { _, c, r -> c.isNotEmpty() && c.first() != null && c.last() == null && r == true },
            { _, c, r -> c.isNotEmpty() && c.first() == null && c.last() != null && r == true },
            { _, c, r -> c.isNotEmpty() && c.none { it == null } && r == false }
        )
    }

    @Test
    fun testAllMatchExample() = disableTest("Index 1 out of bounds for length 1 | URegistersStack.writeRegister") {
        checkDiscoveredProperties(
            BaseStreamExample::allMatchExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r == true },
            { _, c, r -> c.isNotEmpty() && c.all { it == null } && r == true },
            { _, c, r -> c.isNotEmpty() && c.first() != null && c.last() == null && r == false },
            { _, c, r -> c.isNotEmpty() && c.first() == null && c.last() != null && r == false },
            { _, c, r -> c.isNotEmpty() && c.none { it == null } && r == false }
        )
    }

    @Test
    fun testNoneMatchExample() = disableTest("Index 1 out of bounds for length 1 | URegistersStack.writeRegister") {
        checkDiscoveredProperties(
            BaseStreamExample::noneMatchExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r == true },
            { _, c, r -> c.isNotEmpty() && c.all { it == null } && r == false },
            { _, c, r -> c.isNotEmpty() && c.first() != null && c.last() == null && r == false },
            { _, c, r -> c.isNotEmpty() && c.first() == null && c.last() != null && r == false },
            { _, c, r -> c.isNotEmpty() && c.none { it == null } && r == true }
        )
    }

    @Test
    fun testFindFirstExample() = disableTest("Some properties were not discovered at positions (from 0): [0, 1, 2]") {
        checkDiscoveredPropertiesWithExceptions(
            BaseStreamExample::findFirstExample,
            eq(3),
            { _, c, r -> c.isEmpty() && r.getOrThrow() == Optional.empty<Int>() },
            { _, c: List<Int?>, r -> c.isNotEmpty() && c.first() == null && r.isException<NullPointerException>() },
            { _, c, r -> c.isNotEmpty() && r.getOrThrow() == Optional.of(c.first()) },
        )
    }

    @Test
    fun testIteratorExample() = disableTest("Some properties were not discovered at positions (from 0): [0, 1, 2]") {
        checkDiscoveredPropertiesWithExceptions(
            BaseStreamExample::iteratorSumExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r.getOrThrow() == 0 },
            { _, c, r -> null in c && r.isException<NullPointerException>() },
            { _, c, r -> c.isNotEmpty() && null !in c && r.getOrThrow() == c.sum() }
        )
    }

    @Test
    fun testStreamOfExample() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
        checkDiscoveredProperties(
            BaseStreamExample::streamOfExample,
            ignoreNumberOfAnalysisResults,
            // NOTE: the order of the matchers is important because Stream could be used only once
            { _, c, r -> c.isNotEmpty() && c.contentEquals(r!!.toArray()) },
            { _, c, r -> c.isEmpty() && Stream.empty<Int>().toArray().contentEquals(r!!.toArray()) },
        )
    }

    @Test
    fun testClosedStreamExample() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredPropertiesWithExceptions(
            BaseStreamExample::closedStreamExample,
            eq(1),
            { _, _, r -> r.isException<IllegalStateException>() },
        )
    }

    @Test
    fun testCustomCollectionStreamExample() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
        checkDiscoveredProperties(
            BaseStreamExample::customCollectionStreamExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r == 0L },
            { _, c, r -> c.isNotEmpty() && c.size.toLong() == r }, // TODO failed coverage calculation
        )
    }

    @Test
    fun testAnyCollectionStreamExample() = disableTest("Some properties were not discovered at positions (from 0): [0, 1]") {
        checkDiscoveredProperties(
            BaseStreamExample::anyCollectionStreamExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r == 0L },
            { _, c, r -> c.isNotEmpty() && c.size.toLong() == r },
        )
    }

    @Test
    fun testGenerateExample() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredProperties(
            BaseStreamExample::generateExample,
            ignoreNumberOfAnalysisResults,
            { _, r -> r != null && r.contentEquals(Array(10) { 42 }) }
        )
    }

    @Test
    fun testIterateExample() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredProperties(
            BaseStreamExample::iterateExample,
            ignoreNumberOfAnalysisResults,
            { _, r -> r != null && r.contentEquals(Array(10) { i -> 42 + i }) }
        )
    }

    @Test
    fun testConcatExample() = disableTest("Some properties were not discovered at positions (from 0): [0]") {
        checkDiscoveredProperties(
            BaseStreamExample::concatExample,
            ignoreNumberOfAnalysisResults,
            { _, r -> r != null && r.contentEquals(Array(10) { 42 } + Array(10) { i -> 42 + i }) }
        )
    }
}

internal fun <E : Comparable<E>> Sequence<E>.isSorted(): Boolean = zipWithNext { a, b -> a <= b }.all { it }

/**
 * Avoid conflict with java.util.stream.Stream.toList (available since Java 16 only)
 */
fun <T> Stream<T>.asList(): List<T> = collect(Collectors.toList<T>())
