package org.usvm.samples.mixed

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq


internal class PrivateConstructorExampleTest : JavaMethodTestRunner() {

    /**
     * Two branches need to be covered:
     * 1. argument must be <= a - b,
     * 2. argument must be > a - b
     *
     * a and b are fields of the class under test
     */
    @Test
    fun testLimitedSub() {
        checkThisAndParamsMutations(
            PrivateConstructorExample::limitedSub,
            eq(2),
            { caller, limit, _, _, r -> caller.a - caller.b >= limit && r == caller.a - caller.b },
            { caller, limit, _, _, r -> caller.a - caller.b < limit && r == limit }, // TODO: Method coverage with `this` parameter isn't supported
            checkMode = CheckMode.MATCH_PROPERTIES
        )
    }
}
