package org.usvm.samples.objects

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq

class TestStatics : JavaMethodTestRunner() {
    @Test
    fun `Test static field access`() {
        checkDiscoveredProperties(
            ObjectWithStatics::staticsAreEqual,
            eq(1),
            { _, r -> r == 0 },
        )
    }

    @Test
    fun `Test static field write`() {
        checkDiscoveredPropertiesWithStatics(
            ObjectWithStatics::mutateStatics,
            eq(1),
            { _, r, staticsBefore, staticsAfter ->
                val staticBefore = staticsBefore.entries.single().value.single().value as Int
                val staticAfter = staticsAfter.entries.single().value.single().value as Int

                r == 1 && staticBefore == staticAfter - 1
            },
            checkMode = CheckMode.MATCH_PROPERTIES
        )
    }

    @Test
    fun `Test mutable primitive static field`() {
        checkDiscoveredPropertiesWithStatics(
            ObjectWithStatics::useMutablePrimitiveStaticField,
            eq(2),
            { _, r, staticsBefore, _ -> r == 0 && staticsBefore.entries.single().value.single().value == 17 },
            { _, r, staticsBefore, _ -> r == 1 && staticsBefore.entries.single().value.single().value != 17 },
            checkMode = CheckMode.MATCH_PROPERTIES
        )
    }

    @Test
    fun `Test final primitive static field`() {
        checkDiscoveredProperties(
            ObjectWithStatics::useFinalPrimitiveStaticField,
            eq(1),
            { _, r -> r == 0 },
        )
    }
}
