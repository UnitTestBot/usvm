package org.usvm.samples.objects

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.isException


internal class SimpleClassExampleTest : JavaMethodTestRunner() {
    @Test
    fun simpleConditionTest() {
        checkDiscoveredProperties(
            SimpleClassExample::simpleCondition,
            ignoreNumberOfAnalysisResults,
            { _, c, _ -> c == null }, // NPE
            { _, c, r -> !(c.a < 5 && c.b > 10) && r == 3 },
            { _, c, r -> c.a < 5 && c.b > 10 && r == 0 }, // otherwise we overwrite original values
        )
    }

    /**
     * Additional bytecode instructions between IFs, because of random, makes different order of executing the branches,
     * that affects their number. Changing random seed in PathSelector can explore 6th branch
     *
     * @see multipleFieldAccessesTest
     */
    @Test
    fun singleFieldAccessTest() {
        checkDiscoveredProperties(
            SimpleClassExample::singleFieldAccess,
            ignoreNumberOfAnalysisResults,
            { _, c, _ -> c == null }, // NPE
            { _, c, r -> c.a == 2 && c.b == 3 && r == 0 },
            { _, c, r -> c.a == 3 && c.b == 5 && r == 1 },
            { _, c, r -> !(c.a == 3 && c.b == 5) && !(c.a == 2 && c.b == 3) && r == 2 }
        )
    }

    /**
     * Additional bytecode instructions between IFs, because of random, makes different order of executing the branches,
     * that affects their number
     */
    @Test
    fun multipleFieldAccessesTest() {
        checkDiscoveredProperties(
            SimpleClassExample::multipleFieldAccesses,
            ignoreNumberOfAnalysisResults,
            { _, c, _ -> c == null }, // NPE
            { _, c, r -> c.a == 3 && c.b == 5 && r == 1 },
            { _, c, r -> !(c.a == 2 && c.b == 3) && !(c.a == 3 && c.b == 5) && r == 2 },
            { _, c, r -> c.a == 2 && c.b == 3 && r == 0 }
        )
    }

    @Test
    fun immutableFieldAccessTest() {
        checkDiscoveredPropertiesWithExceptions(
            SimpleClassExample::immutableFieldAccess,
            ignoreNumberOfAnalysisResults,
            { _, c, r -> c == null && r.isException<NullPointerException>() },
            { _, c, r -> c.b == 10 && r.getOrNull() == 0 },
            { _, c, r -> c.b != 10 && r.getOrNull() == 1 }
        )
    }
}