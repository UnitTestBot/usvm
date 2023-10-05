package org.usvm.samples.substitution

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


class StaticsSubstitutionTest : JavaMethodTestRunner() {

    @Test
    @Disabled("Undefined behavior for executor")
    fun lessThanZeroWithSubstitution() {
        checkDiscoveredProperties(
            StaticSubstitutionExamples::lessThanZero,
            eq(2),
            { _, r -> r != 0 },
            { _, r -> r == 0 },
        )
    }

    // TODO unsupported
//    @Test
//    fun lessThanZeroWithoutSubstitution() {
//            checkWithoutStaticsSubstitution(
//                StaticSubstitutionExamples::lessThanZero,
//                eq(1),
//                { _, r -> r != 0 },
//            )
//        }
    }