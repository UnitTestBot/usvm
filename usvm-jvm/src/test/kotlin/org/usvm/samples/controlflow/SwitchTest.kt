package org.usvm.samples.controlflow

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.test.util.checkers.ge
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import java.math.RoundingMode.CEILING
import java.math.RoundingMode.DOWN
import java.math.RoundingMode.HALF_DOWN
import java.math.RoundingMode.HALF_EVEN
import java.math.RoundingMode.HALF_UP

internal class SwitchTest : JavaMethodTestRunner() {
    @Test
    fun testSimpleSwitch() {
        checkDiscoveredProperties(
            Switch::simpleSwitch,
            ge(4),
            { _, x, r -> x == 10 && r == 10 },
            { _, x, r -> (x == 11 || x == 12) && r == 12 }, // fall-through has it's own branch
            { _, x, r -> x == 13 && r == 13 },
            { _, x, r -> x !in 10..13 && r == -1 }, // one for default is enough
        )
    }

    @Test
    fun testSimpleSwitchWithPrecondition() {
        checkDiscoveredProperties(
            Switch::simpleSwitchWithPrecondition,
            ge(4),
            { _, x, r -> (x == 10 || x == 11) && r == 0 },
            { _, x, r -> x == 12 && r == 12 },
            { _, x, r -> x == 13 && r == 13 },
            { _, x, r -> x !in 10..13 && r == -1 }, // one for default is enough
        )
    }

    @Test
    fun testLookupSwitch() {
        checkDiscoveredProperties(
            Switch::lookupSwitch,
            ge(4),
            { _, x, r -> x == 0 && r == 0 },
            { _, x, r -> (x == 10 || x == 20) && r == 20 }, // fall-through has it's own branch
            { _, x, r -> x == 30 && r == 30 },
            { _, x, r -> x !in setOf(0, 10, 20, 30) && r == -1 } // one for default is enough
        )
    }

    @Test
    fun testEnumSwitch() {
        checkDiscoveredProperties(
            Switch::enumSwitch,
            ignoreNumberOfAnalysisResults,
            { _, m, _ -> m == null }, // NPE
            { _, m, r -> m == Switch.EnumExample.SUCCESS && r == 1 },
            { _, m, r -> m == Switch.EnumExample.ERROR && r == 2 },
        )
    }

    @Test
    fun testEnumCustomField() {
        checkDiscoveredProperties(
            Switch::enumCustomField,
            ignoreNumberOfAnalysisResults,
            { _, m, r -> m == null && r == 42 },
            { _, m, r -> (m == Switch.EnumExample.SUCCESS || m == Switch.EnumExample.ERROR) && m.x == r }
        )
    }

    @Test
    fun testEnumName() {
        checkDiscoveredProperties(
            Switch::enumName,
            ignoreNumberOfAnalysisResults,
            { _, m, s -> m == null && s == "" },
            { _, m, r -> (m == Switch.EnumExample.SUCCESS || m == Switch.EnumExample.ERROR) && m.name == r }
        )
    }

    @Test
    fun testUnusedEnumParameter() {
        checkDiscoveredProperties(
            Switch::unusedEnumParameter,
            ignoreNumberOfAnalysisResults,
            { _, m, r -> m == null && r == 0 },
            { _, m, r -> (m == Switch.EnumExample.SUCCESS || m == Switch.EnumExample.ERROR) && r == 42 }
        )
    }

    @Test
    @Disabled("TODO this test cannot be supported for now - it uses an `artificial` enum.")
    fun testCustomEnumName() {
        checkDiscoveredProperties(
            Switch::customEnumName,
            ignoreNumberOfAnalysisResults,
            { _, m, s -> m == null && s == "" },
            { _, m, r -> (m == CustomEnum.VALUE1 || m == CustomEnum.VALUE2) && m.name() == r }
        )
    }

//    @Test
//    @Disabled("TODO support statics memory region. There is the same problem as for enums - constraints are set for array with stores, not for an empty array.")
//    fun testRoundingModeSwitch() {
//        checkDiscoveredProperties(
//            Switch::roundingModeSwitch,
//            ignoreNumberOfAnalysisResults,
//            { _, m, _ -> m == null }, // NPE
//            { _, m, r -> m == HALF_DOWN && r == 1 }, // We will minimize two of these branches
////            { _, m, r -> m == HALF_EVEN && r == 1 }, // We will minimize two of these branches
////            { _, m, r -> m == HALF_UP && r == 1 }, // We will minimize two of these branches
//            { _, m, r -> m == DOWN && r == 2 },
//            { _, m, r -> m == CEILING && r == 3 },
//            { _, m, r -> m !in setOf(HALF_DOWN, HALF_EVEN, HALF_UP, DOWN, CEILING) && r == -1 },
//        )
//    }

    @Test
    fun testCharToIntSwitch() {
        checkDiscoveredPropertiesWithExceptions(
            Switch::charToIntSwitch,
            ge(8),
            { _, c, r -> c == 'I' && r.getOrThrow() == 1 },
            { _, c, r -> c == 'V' && r.getOrThrow() == 5 },
            { _, c, r -> c == 'X' && r.getOrThrow() == 10 },
            { _, c, r -> c == 'L' && r.getOrThrow() == 50 },
            { _, c, r -> c == 'C' && r.getOrThrow() == 100 },
            { _, c, r -> c == 'D' && r.getOrThrow() == 500 },
            { _, c, r -> c == 'M' && r.getOrThrow() == 1000 },
            { _, _, r -> r.exceptionOrNull() is IllegalArgumentException },
        )
    }

    @Test
    @Disabled("An operation is not implemented: Not yet implemented. Support strings")
    fun testStringSwitch() {
        checkDiscoveredProperties(
            Switch::stringSwitch,
            ge(4),
            { _, s, r -> s == "ABC" && r == 1 },
            { _, s, r -> s == "DEF" && r == 2 },
            { _, s, r -> s == "GHJ" && r == 2 },
            { _, _, r -> r == -1 },
        )
    }
}