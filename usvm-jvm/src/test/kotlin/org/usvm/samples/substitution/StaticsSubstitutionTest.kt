package org.usvm.samples.substitution

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.util.disableTest


class StaticsSubstitutionTest : JavaMethodTestRunner() {

    @Test
    // todo: treat static fields as input values
    fun lessThanZeroWithSubstitution() = disableTest("Some properties were not discovered at positions (from 0): [1]") {
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