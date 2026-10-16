package org.usvm.samples.objects

import org.junit.jupiter.api.Test
import org.usvm.StateCollectionStrategy
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.isException

internal class ConditionalCloneTest : JavaMethodTestRunner() {
    init {
        options = options.copy(
            stateCollectionStrategy = StateCollectionStrategy.ALL,
            stopOnCoverage = 0,
            throwExceptionOnStepFailure = true,
        )
    }

    @Test
    fun `array references selected by a symbolic index clone independently`() {
        checkDiscoveredProperties(
            ConditionalClone::arrays,
            ignoreNumberOfAnalysisResults,
            { _, index, result -> index !in 0..1 && result == -1 },
            { _, index, result -> index == 0 && result == 301 },
            { _, index, result -> index == 1 && result == 702 },
            invariants = arrayOf({ _, index, result ->
                val expected = when (index) {
                    0 -> 301
                    1 -> 702
                    else -> -1
                }
                result == expected
            }),
        )
    }

    @Test
    fun `null array alternative throws while the non-null alternative is cloned`() {
        checkDiscoveredPropertiesWithExceptions(
            ConditionalClone::nullableArrays,
            ignoreNumberOfAnalysisResults,
            { _, index, result -> index !in 0..1 && result.getOrNull() == -1 },
            { _, index, result -> index == 0 && result.isException<NullPointerException>() },
            { _, index, result -> index == 1 && result.getOrNull() == 7 },
            invariants = arrayOf({ _, index, result ->
                when (index) {
                    0 -> result.isException<NullPointerException>()
                    1 -> result.getOrNull() == 7
                    else -> result.getOrNull() == -1
                }
            }),
        )
    }

    @Test
    fun `object clones are distinct shallow copies of the selected reference`() {
        checkDiscoveredProperties(
            ConditionalClone::objects,
            ignoreNumberOfAnalysisResults,
            { _, index, result -> index !in 0..1 && result == -1 },
            { _, index, result -> index == 0 && result == 3 },
            { _, index, result -> index == 1 && result == 7 },
            invariants = arrayOf({ _, index, result ->
                val expected = when (index) {
                    0 -> 3
                    1 -> 7
                    else -> -1
                }
                result == expected
            }),
        )
    }
}
