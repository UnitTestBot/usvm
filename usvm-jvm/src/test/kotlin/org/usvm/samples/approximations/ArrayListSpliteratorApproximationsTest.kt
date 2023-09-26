package org.usvm.samples.approximations

import approximations.java.util.ArrayListSpliterator_Tests
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

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
    @Disabled("Impossible exception in VM clinit")
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
    @Disabled("Index 3 out of bounds for length 3")
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
    @Disabled("Impossible exception in VM clinit")
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
    @Disabled("Unexpected expr of type void: JcLambdaExpr")
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
    @Disabled("Unexpected expr of type void: JcLambdaExpr")
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
