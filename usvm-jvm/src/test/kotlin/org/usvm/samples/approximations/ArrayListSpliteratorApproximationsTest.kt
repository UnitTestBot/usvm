package org.usvm.samples.approximations

import approximations.java.util.ArrayListSpliterator_Tests
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.test.util.checkers.eq

class ArrayListSpliteratorApproximationsTest : ApproximationsTestRunner() {
    @Test
    fun testCharacteristics() {
        checkDiscoveredPropertiesWithExceptions(
            ArrayListSpliterator_Tests::test_characteristics_0,
            eq(2),
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
    @Disabled("Index 3 out of bounds for length 3")
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
        checkDiscoveredPropertiesWithExceptions(
            ArrayListSpliterator_Tests::test_getExactSizeIfKnown_0,
            eq(3),
            { o, _ -> o == 0 },
            { o, _ -> o == 1 },
            invariants = arrayOf(
                { o, r -> o !in 0..1 || r.getOrThrow() == o }
            )
        )
    }

    @Test
    fun testHasCharacteristics() {
        checkDiscoveredPropertiesWithExceptions(
            ArrayListSpliterator_Tests::test_hasCharacteristics_0,
            eq(2),
            { o, _ -> o == 0 },
            invariants = arrayOf(
                { o, r -> o != 0 || r.getOrThrow() == 0 }
            )
        )
    }

    @Test
    @Disabled("Unexpected expr of type void: JcLambdaExpr")
    fun testTryAdvance() {
        checkDiscoveredPropertiesWithExceptions(
            ArrayListSpliterator_Tests::test_tryAdvance_0,
            eq(4),
            { o, _ -> o == 0 },
            { o, _ -> o == 1 },
            { o, _ -> o == 2 },
            invariants = arrayOf(
                { o, r -> o !in 0..2 || r.getOrThrow() == o }
            )
        )
    }

    @Test
    @Disabled("Unexpected expr of type void: JcLambdaExpr")
    fun testTrySplit() {
        checkDiscoveredPropertiesWithExceptions(
            ArrayListSpliterator_Tests::test_tryAdvance_0,
            eq(4),
            { o, _ -> o == 0 },
            { o, _ -> o == 1 },
            { o, _ -> o == 2 },
            invariants = arrayOf(
                { o, r -> o !in 0..2 || r.getOrThrow() == o }
            )
        )
    }
}
