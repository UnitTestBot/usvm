package org.usvm.samples.stream

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.isException

import java.util.Optional
import java.util.stream.Collectors
import java.util.stream.Stream

@Disabled("Still too complex")
class BaseStreamExampleTest : JavaMethodTestRunner() {
    @Test
    fun testReturningStreamAsParameterExample() {
        checkDiscoveredProperties(
            BaseStreamExample::returningStreamAsParameterExample,
            eq(1),
            { _, s, r -> s != null && s.asList() == r!!.asList() },
        )
    }

    @Test
    fun testFilterExample() {
        checkDiscoveredProperties(
            BaseStreamExample::filterExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> null !in c && r == false },
            { _, c, r -> null in c && r == true },
        )
    }

    @Test
    fun testMapExample() {
        checkDiscoveredPropertiesWithExceptions(
            BaseStreamExample::mapExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> null in c && r.isException<NullPointerException>() },
            { _, c, r -> r.getOrThrow().contentEquals(c.map { it * 2 }.toTypedArray()) },
        )
    }

    @Test
    @Tag("slow")
    @Disabled("Not implemented: virtual calls with abstract methods")
    fun testMapToIntExample() {
        checkDiscoveredPropertiesWithExceptions(
            BaseStreamExample::mapToIntExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> null in c && r.isException<NullPointerException>() },
            { _, c, r -> r.getOrThrow().contentEquals(c.map { it.toInt() }.toIntArray()) },
        )
    }

    @Test
    @Tag("slow")
    @Disabled("Not implemented: virtual calls with abstract methods")
    fun testMapToLongExample() {
        checkDiscoveredPropertiesWithExceptions(
            BaseStreamExample::mapToLongExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> null in c && r.isException<NullPointerException>() },
            { _, c, r -> r.getOrThrow().contentEquals(c.map { it.toLong() }.toLongArray()) }
        )
    }

    @Test
    @Tag("slow")
    @Disabled("Not implemented: virtual calls with abstract methods")
    fun testMapToDoubleExample() {
        checkDiscoveredPropertiesWithExceptions(
            BaseStreamExample::mapToDoubleExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> null in c && r.isException<NullPointerException>() },
            { _, c, r -> r.getOrThrow().contentEquals(c.map { it.toDouble() }.toDoubleArray()) }
        )
    }

    @Test
    fun testFlatMapExample() {
        checkDiscoveredProperties(
            BaseStreamExample::flatMapExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> r.contentEquals(c.flatMap { listOf(it, it) }.toTypedArray()) },
        )
    }

    @Test
    @Tag("slow")
    @Disabled("Not implemented: virtual calls with abstract methods")
    fun testFlatMapToIntExample() {
        checkDiscoveredProperties(
            BaseStreamExample::flatMapToIntExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> r.contentEquals(c.flatMap { listOf(it?.toInt() ?: 0, it?.toInt() ?: 0) }.toIntArray()) },
        )
    }

    @Test
    fun testFlatMapToLongExample() {
        checkDiscoveredProperties(
            BaseStreamExample::flatMapToLongExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> r.contentEquals(c.flatMap { listOf(it?.toLong() ?: 0L, it?.toLong() ?: 0L) }.toLongArray()) },
        )
    }

    @Test
    fun testFlatMapToDoubleExample() {
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
    @Disabled("Not implemented: virtual calls with abstract methods")
    fun testDistinctExample() {
        checkDiscoveredProperties(
            BaseStreamExample::distinctExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c == c.distinct() && r == false },
            { _, c, r -> c != c.distinct() && r == true },
        )
    }

    @Test
    @Tag("slow")
    @Disabled("Not implemented: virtual calls with abstract methods")
    fun testSortedExample() {
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
    fun testLimitExample() {
        checkDiscoveredProperties(
            BaseStreamExample::limitExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.size <= 5 && c.toTypedArray().contentEquals(r) },
            { _, c, r -> c.size > 5 && c.take(5).toTypedArray().contentEquals(r) },
        )
    }

    @Test
    fun testSkipExample() {
        checkDiscoveredProperties(
            BaseStreamExample::skipExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.size > 5 && c.drop(5).toTypedArray().contentEquals(r) },
            { _, c, r -> c.size <= 5 && r != null && r.isEmpty() },
        )
    }

    // TODO unsupported
//    @Test
//    fun testForEachExample() {
//        checkThisAndStaticsAfter(
//            BaseStreamExample::forEachExample,
//            ignoreNumberOfAnalysisResults,
//            *streamConsumerStaticsMatchers,
//        )
//    }

    @Test
    fun testToArrayExample() {
        checkDiscoveredProperties(
            BaseStreamExample::toArrayExample,
            eq(2),
            { _, c, r -> c.toTypedArray().contentEquals(r) },
        )
    }

    @Test
    fun testReduceExample() {
        checkDiscoveredProperties(
            BaseStreamExample::reduceExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r == 42 },
            { _, c, r -> c.isNotEmpty() && r == c.sum() + 42 },
        )
    }

    @Test
    fun testOptionalReduceExample() {
        checkDiscoveredPropertiesWithExceptions(
            BaseStreamExample::optionalReduceExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r.getOrThrow() == Optional.empty<Int>() },
            { _, c, r -> c.isNotEmpty() && c.single() == null && r.isException<NullPointerException>() },
            { _, c, r -> c.isNotEmpty() && r.getOrThrow() == Optional.of<Int>(c.sum()) },
        )
    }

    @Test
    fun testComplexReduceExample() {
        checkDiscoveredProperties(
            BaseStreamExample::complexReduceExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && c.sumOf { it.toDouble() } + 42.0 == r },
            { _, c: List<Int?>, r -> c.isNotEmpty() && c.sumOf { it?.toDouble() ?: 0.0 } + 42.0 == r },
        )
    }

    @Test
    fun testCollectorExample() {
        checkDiscoveredProperties(
            BaseStreamExample::collectorExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.toSet() == r },
        )
    }

    @Test
    fun testCollectExample() {
        checkDiscoveredPropertiesWithExceptions(
            BaseStreamExample::collectExample,
            eq(2), // 3 executions instead of 2 expected
            { _, c, r -> null in c && r.isException<NullPointerException>() },
            { _, c, r -> null !in c && c.sum() == r.getOrThrow() },
        )
    }

    @Test
    fun testMinExample() {
        checkDiscoveredPropertiesWithExceptions(
            BaseStreamExample::minExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r.getOrThrow() == Optional.empty<Int>() },
            { _, c, r -> c.isNotEmpty() && c.all { it == null } && r.isException<NullPointerException>() },
            { _, c, r -> c.isNotEmpty() && r.getOrThrow() == Optional.of<Int>(c.minOrNull()!!) },
        )
    }

    @Test
    fun testMaxExample() {
        checkDiscoveredPropertiesWithExceptions(
            BaseStreamExample::maxExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r.getOrThrow() == Optional.empty<Int>() },
            { _, c, r -> c.isNotEmpty() && c.all { it == null } && r.isException<NullPointerException>() },
            { _, c, r -> c.isNotEmpty() && r.getOrThrow() == Optional.of<Int>(c.maxOrNull()!!) },
        )
    }

    @Test
    fun testCountExample() {
        checkDiscoveredProperties(
            BaseStreamExample::countExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r == 0L },
            { _, c, r -> c.isNotEmpty() && c.size.toLong() == r },
        )
    }

    @Test
    fun testAnyMatchExample() {
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
    fun testAllMatchExample() {
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
    fun testNoneMatchExample() {
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
    fun testFindFirstExample() {
        checkDiscoveredPropertiesWithExceptions(
            BaseStreamExample::findFirstExample,
            eq(3),
            { _, c, r -> c.isEmpty() && r.getOrThrow() == Optional.empty<Int>() },
            { _, c: List<Int?>, r -> c.isNotEmpty() && c.first() == null && r.isException<NullPointerException>() },
            { _, c, r -> c.isNotEmpty() && r.getOrThrow() == Optional.of(c.first()) },
        )
    }

    @Test
    fun testIteratorExample() {
        checkDiscoveredPropertiesWithExceptions(
            BaseStreamExample::iteratorSumExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r.getOrThrow() == 0 },
            { _, c, r -> null in c && r.isException<NullPointerException>() },
            { _, c, r -> c.isNotEmpty() && null !in c && r.getOrThrow() == c.sum() }
        )
    }

    @Test
    fun testStreamOfExample() {
        checkDiscoveredProperties(
            BaseStreamExample::streamOfExample,
            ignoreNumberOfAnalysisResults,
            // NOTE: the order of the matchers is important because Stream could be used only once
            { _, c, r -> c.isNotEmpty() && c.contentEquals(r!!.toArray()) },
            { _, c, r -> c.isEmpty() && Stream.empty<Int>().toArray().contentEquals(r!!.toArray()) },
        )
    }

    @Test
    fun testClosedStreamExample() {
        checkDiscoveredPropertiesWithExceptions(
            BaseStreamExample::closedStreamExample,
            eq(1),
            { _, _, r -> r.isException<IllegalStateException>() },
        )
    }

    @Test
    fun testCustomCollectionStreamExample() {
        checkDiscoveredProperties(
            BaseStreamExample::customCollectionStreamExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r == 0L },
            { _, c, r -> c.isNotEmpty() && c.size.toLong() == r }, // TODO failed coverage calculation
        )
    }

    @Test
    fun testAnyCollectionStreamExample() {
        checkDiscoveredProperties(
            BaseStreamExample::anyCollectionStreamExample,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c.isEmpty() && r == 0L },
            { _, c, r -> c.isNotEmpty() && c.size.toLong() == r },
        )
    }

    @Test
    fun testGenerateExample() {
        checkDiscoveredProperties(
            BaseStreamExample::generateExample,
            ignoreNumberOfAnalysisResults,
            { _, r -> r != null && r.contentEquals(Array(10) { 42 }) }
        )
    }

    @Test
    fun testIterateExample() {
        checkDiscoveredProperties(
            BaseStreamExample::iterateExample,
            ignoreNumberOfAnalysisResults,
            { _, r -> r != null && r.contentEquals(Array(10) { i -> 42 + i }) }
        )
    }

    @Test
    fun testConcatExample() {
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
