package org.usvm.samples.pointers

import org.junit.jupiter.api.Test
import org.usvm.samples.GoNativeTestRunner
import org.usvm.samples.GoSample

class NamedPointerRegressionTest : GoNativeTestRunner() {
    @Test
    @GoSample(method = "namedPointerAssertionZero", fixture = "regressions")
    fun namedPointerAssertionZero() = checkNative(method = "namedPointerAssertionZero")
}
