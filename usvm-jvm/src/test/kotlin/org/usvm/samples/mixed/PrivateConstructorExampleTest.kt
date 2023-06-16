package org.usvm.samples.mixed

import org.usvm.samples.JavaMethodTestRunner


internal class PrivateConstructorExampleTest : JavaMethodTestRunner() {

    /**
     * Two branches need to be covered:
     * 1. argument must be <= a - b,
     * 2. argument must be > a - b
     *
     * a and b are fields of the class under test
     */
    // TODO unsupported matchers

//    @Test
//    fun testLimitedSub() {
//        checkWithThis(
//            PrivateConstructorExample::limitedSub,
//            eq(2),
//            { caller, limit, r -> caller.a - caller.b >= limit && r == caller.a - caller.b },
//            { caller, limit, r -> caller.a - caller.b < limit && r == limit }, // TODO: Method coverage with `this` parameter isn't supported
//        )
//    }
}
