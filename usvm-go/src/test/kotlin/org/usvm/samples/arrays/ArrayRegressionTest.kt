package org.usvm.samples.arrays

import org.junit.jupiter.api.Test
import org.usvm.samples.GoNativeTestRunner
import org.usvm.samples.GoSample

class ArrayRegressionTest : GoNativeTestRunner() {
    @Test
    @GoSample(method = "narrowIndex", fixture = "regressions")
    fun narrowIndex() = checkNative(method = "narrowIndex")
}
