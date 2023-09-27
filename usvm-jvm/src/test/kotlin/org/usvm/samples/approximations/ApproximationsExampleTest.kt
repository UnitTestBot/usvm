package org.usvm.samples.approximations

import org.junit.jupiter.api.Test
import org.usvm.test.util.checkers.eq
import org.usvm.util.isException

class ApproximationsExampleTest : ApproximationsTestRunner() {

    @Test
    fun testArrayListModification() {
        checkDiscoveredPropertiesWithExceptions(
            ApproximationsExample::modifyList,
            eq(6),
            { _, o, r -> o == 0 && r.isException<IndexOutOfBoundsException>() },
            { _, o, _ -> o == 1 },
            { _, o, _ -> o == 2 },
            { _, o, _ -> o == 3 },
            { _, o, _ -> o == 4 },
            invariants = arrayOf(
                { _, execution, r -> execution !in 1..4 || r.getOrThrow() == execution }
            )
        )
    }

    @Test
    fun testOptionalDouble() {
        checkDiscoveredPropertiesWithExceptions(
            ApproximationsExample::testOptionalDouble,
            eq(4),
            { _, o, _ -> o == 0 },
            { _, o, _ -> o == 1 },
            { _, o, _ -> o == 2 },
            invariants = arrayOf(
                { _, execution, r -> execution !in 0..2 || r.getOrThrow() == execution }
            )
        )
    }
}
