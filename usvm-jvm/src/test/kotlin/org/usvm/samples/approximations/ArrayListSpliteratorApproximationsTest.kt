package org.usvm.samples.approximations

import approximations.java.util.ArrayListSpliterator_Tests
import approximations.java.util.ArrayList_Tests
import approximations.java.util.OptionalDouble_Tests
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.isException

@Disabled("Incorrect test examples")
class ArrayListSpliteratorApproximationsTest : ApproximationsTestRunner() {
    @Test
    fun testCharacteristics() {
        with(FixedExecutionVerifier(1)) {
            checkDiscoveredPropertiesWithExceptions(
                ArrayListSpliterator_Tests::test_characteristics_0,
                ignoreNumberOfAnalysisResults,
                { o, r -> verifyStatus(o, r.getOrThrow()) },
            )
            check()
        }
    }

    @Test
    fun testEstimateSize() {
        with(FixedExecutionVerifier(2)) {
            checkDiscoveredPropertiesWithExceptions(
                ArrayListSpliterator_Tests::test_estimateSize_0,
                ignoreNumberOfAnalysisResults,
                { o, r -> verifyStatus(o, r.getOrThrow()) },
            )
            check()
        }
    }

    @Test
    fun testForEachRemaining() {
        with(FixedExecutionVerifier(2)) {
            checkDiscoveredPropertiesWithExceptions(
                ArrayListSpliterator_Tests::test_forEachRemaining_0,
                ignoreNumberOfAnalysisResults,
                { o, r -> verifyStatus(o, r.getOrThrow()) },
            )
            check()
        }
    }

    @Test
    fun testGetExactSizeIfKnown() {
        with(FixedExecutionVerifier(2)) {
            checkDiscoveredPropertiesWithExceptions(
                ArrayListSpliterator_Tests::test_getExactSizeIfKnown_0,
                ignoreNumberOfAnalysisResults,
                { o, r -> verifyStatus(o, r.getOrThrow()) },
            )
            check()
        }
    }

    @Test
    fun testHasCharacteristics() {
        with(FixedExecutionVerifier(1)) {
            checkDiscoveredPropertiesWithExceptions(
                ArrayListSpliterator_Tests::test_hasCharacteristics_0,
                ignoreNumberOfAnalysisResults,
                { o, r -> verifyStatus(o, r.getOrThrow()) },
            )
            check()
        }
    }

    @Test
    fun testTryAdvance() {
        with(FixedExecutionVerifier(3)) {
            checkDiscoveredPropertiesWithExceptions(
                ArrayListSpliterator_Tests::test_tryAdvance_0,
                ignoreNumberOfAnalysisResults,
                { o, r -> verifyStatus(o, r.getOrThrow()) },
            )
            check()
        }
    }

    @Test
    fun testTrySplit() {
        with(FixedExecutionVerifier(2)) {
            checkDiscoveredPropertiesWithExceptions(
                ArrayListSpliterator_Tests::test_tryAdvance_0,
                ignoreNumberOfAnalysisResults,
                { o, r -> verifyStatus(o, r.getOrThrow()) },
            )
            check()
        }
    }
}
