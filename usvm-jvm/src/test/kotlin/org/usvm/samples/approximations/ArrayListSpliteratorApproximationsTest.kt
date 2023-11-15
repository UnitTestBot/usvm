package org.usvm.samples.approximations

import approximations.java.util.ArrayListSpliterator_Tests
import org.junit.jupiter.api.Test
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class ArrayListSpliteratorApproximationsTest : ApproximationsTestRunner() {
    @Test
    fun testCharacteristics() {
        checkDiscoveredPropertiesWithExceptions(
            ArrayListSpliterator_Tests::test_characteristics_0,
            ignoreNumberOfAnalysisResults,
            { o, _ -> o == 0 },
            invariants = arrayOf(
                { o, r -> o != 0 || r.getOrThrow() == 0 }
            )
        )
    }

    @Test
    fun testEstimateSize() {
        checkDiscoveredPropertiesWithExceptions(
            ArrayListSpliterator_Tests::test_estimateSize_0,
            eq(3),
            { o, _ -> o == 0 },
            { o, _ -> o == 1 },
            invariants = arrayOf(
                { o, r -> o !in 0..1 || r.getOrThrow() == o }
            )
        )
    }

    @Test
    fun testForEachRemaining() {
        checkDiscoveredPropertiesWithExceptions(
            ArrayListSpliterator_Tests::test_forEachRemaining_0,
            eq(3),
            { o, _ -> o == 0 },
            { o, _ -> o == 1 },
            invariants = arrayOf(
                { o, r -> o !in 0..1 || r.getOrThrow() == o }
            )
        )
    }

    @Test
    fun testGetExactSizeIfKnown() {
        checkDiscoveredProperties(
            ArrayListSpliterator_Tests::test_getExactSizeIfKnown_0,
            eq(3),
            { o, _ -> o == 0 },
            { o, _ -> o == 1 },
            invariants = arrayOf(
                { o, r -> o !in 0..1 || r == o }
            )
        )
    }

    @Test
    fun testHasCharacteristics() {
        checkDiscoveredProperties(
            ArrayListSpliterator_Tests::test_hasCharacteristics_0,
            ignoreNumberOfAnalysisResults,
            { o, _ -> o == 0 },
            invariants = arrayOf(
                { o, r -> o != 0 || r == 0 }
            )
        )
    }

    @Test
    fun testTryAdvance() {
        checkDiscoveredProperties(
            ArrayListSpliterator_Tests::test_tryAdvance_0,
            ignoreNumberOfAnalysisResults,
            { o, _ -> o == 0 },
            { o, _ -> o == 1 },
            { o, _ -> o == 2 },
            invariants = arrayOf(
                { o, r -> o !in 0..2 || r == o }
            )
        )
    }

    @Test
    fun testTrySplit() {
        checkDiscoveredPropertiesWithExceptions(
            ArrayListSpliterator_Tests::test_trySplit_0,
            eq(3),
            { o, _ -> o == 0 },
            { o, _ -> o == 1 },
            invariants = arrayOf(
                { o, r -> o !in 0..1 || r.getOrThrow() == o }
            )
        )
    }
}
