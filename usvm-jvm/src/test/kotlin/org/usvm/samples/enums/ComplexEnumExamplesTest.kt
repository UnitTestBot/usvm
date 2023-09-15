package org.usvm.samples.enums

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.samples.enums.ComplexEnumExamples.Color
import org.usvm.samples.enums.ComplexEnumExamples.Color.BLUE
import org.usvm.samples.enums.ComplexEnumExamples.Color.GREEN
import org.usvm.samples.enums.ComplexEnumExamples.Color.RED
import org.usvm.samples.enums.SimpleEnumExample.*
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class ComplexEnumExamplesTest : JavaMethodTestRunner() {
    @Test
    @Disabled("Hangs")
    fun testEnumToEnumMapCountValues() {
        checkDiscoveredProperties(
            ComplexEnumExamples::enumToEnumMapCountValues,
            ignoreNumberOfAnalysisResults,
            { _, m, r -> m.isEmpty() && r == 0 },
            { _, m, r -> m.isNotEmpty() && !m.values.contains(RED) && r == 0 },
            { _, m, r -> m.isNotEmpty() && m.values.contains(RED) && m.values.count { it == RED } == r }
        )
    }

    @Test
    @Disabled("Exceeds timeout")
    fun testEnumToEnumMapCountKeys() {
        checkDiscoveredProperties(
            ComplexEnumExamples::enumToEnumMapCountKeys,
            ignoreNumberOfAnalysisResults,
            { _, m, r -> m.isEmpty() && r == 0 },
            { _, m, r -> m.isNotEmpty() && !m.keys.contains(GREEN) && !m.keys.contains(BLUE) && r == 0 },
            { _, m, r ->
                m.isNotEmpty() && m.keys.intersect(setOf(BLUE, GREEN))
                    .isNotEmpty() && m.keys.count { it == BLUE || it == GREEN } == r
            }
        )
    }

    @Test
    @Disabled("Exceeds timeout")
    fun testEnumToEnumMapCountMatches() {
        checkDiscoveredProperties(
            ComplexEnumExamples::enumToEnumMapCountMatches,
            ignoreNumberOfAnalysisResults,
            { _, m, r -> m.isEmpty() && r == 0 },
            { _, m, r -> m.entries.count { it.key == it.value } == r }
        )
    }

    @Test
    @Disabled("TODO the first matcher does not matches. Discover whether it should or not - perhaps it's an issue with calculating coverage.")
    fun testCountEqualColors() {
        checkDiscoveredProperties(
            ComplexEnumExamples::countEqualColors,
            ignoreNumberOfAnalysisResults,
            { _, a, b, c, r -> a == b && a == c && r == 3 },
            { _, a, b, c, r -> setOf(a, b, c).size == 2 && r == 2 },
            { _, a, b, c, r -> a != b && b != c && a != c && r == 1 }
        )
    }

    @Test
    @Disabled("TODO the first matcher does not matches. Discover whether it should or not - perhaps it's an issue with calculating coverage.")
    fun testCountNullColors() {
        checkDiscoveredProperties(
            ComplexEnumExamples::countNullColors,
            eq(3),
            { _, a, b, r -> a == null && b == null && r == 2 },
            { _, a, b, r -> (a == null) != (b == null) && r == 1 },
            { _, a, b, r -> a != null && b != null && r == 0 },
        )
    }

    @Test
    fun testFindState() {
        checkDiscoveredProperties(
            ComplexEnumExamples::findState,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c in setOf(0, 127, 255) && r != null && r.code == c }
        )
    }

    @Test
    @Disabled("Exceeds timeout")
    fun testCountValuesInArray() {
        fun Color.isCorrectlyCounted(inputs: Array<Color>, counts: Map<Color, Int>): Boolean =
            inputs.count { it == this } == (counts[this] ?: 0)

        checkDiscoveredProperties(
            ComplexEnumExamples::countValuesInArray,
            ignoreNumberOfAnalysisResults,
            { _, cs, r -> cs.isEmpty() && r != null && r.isEmpty() },
            { _, cs, r -> cs.toList().isEmpty() && r != null && r.isEmpty() },
            { _, cs, r -> cs.toList().isNotEmpty() && r != null && Color.values().all { it.isCorrectlyCounted(cs, r) } }
        )
    }

    @Test
    fun testCountRedInArray() {
        checkDiscoveredProperties(
            ComplexEnumExamples::countRedInArray,
            ignoreNumberOfAnalysisResults,
            { _, colors, result -> colors.count { it == RED } == result }
        )
    }

    @Test
    @Disabled("TODO this test cannot be supported for now - it uses an `artificial` enum.")
    fun testCustomEnumName() {
        checkDiscoveredProperties(
            ComplexEnumExamples::customEnumName,
            ignoreNumberOfAnalysisResults,
            { _, m, s -> m == null && s == "" },
            { _, m, r -> (m == CustomEnum.VALUE1 || m == CustomEnum.VALUE2) && m.name() == r }
        )
    }

    @Test
    fun testEnumCustomField() {
        checkDiscoveredProperties(
            ComplexEnumExamples::enumCustomField,
            ignoreNumberOfAnalysisResults,
            { _, m, r -> m == null && r == 42 },
            { _, m, r -> (m == SUCCESS || m == ERROR) && m.x == r }
        )
    }

    @Test
    fun testEnumName() {
        checkDiscoveredProperties(
            ComplexEnumExamples::enumName,
            ignoreNumberOfAnalysisResults,
            { _, m, s -> m == null && s == "" },
            { _, m, r -> (m == SUCCESS || m == ERROR) && m.name == r }
        )
    }

    @Test
    fun testUnusedEnumParameter() {
        checkDiscoveredProperties(
            ComplexEnumExamples::unusedEnumParameter,
            ignoreNumberOfAnalysisResults,
            { _, m, r -> m == null && r == 0 },
            { _, m, r -> (m == SUCCESS || m == ERROR) && r == 42 }
        )
    }
}
