package org.usvm.samples.pointers

import org.junit.jupiter.api.Test
import org.usvm.samples.GoNativeTestRunner
import org.usvm.samples.GoSample

class PointerRegressionTest : GoNativeTestRunner() {
    @Test
    @GoSample(method = "nilPointerConversion", fixture = "regressions")
    fun nilPointerConversion() = checkNative(method = "nilPointerConversion")

    @Test
    @GoSample(method = "pointerConversionAlias", fixture = "regressions")
    fun pointerConversionAlias() = checkNative(method = "pointerConversionAlias")

    @Test
    @GoSample(method = "pointerConversionRoundTrip", fixture = "regressions")
    fun pointerConversionRoundTrip() = checkNative(method = "pointerConversionRoundTrip")

    @Test
    @GoSample(method = "namedPointerConversionAlias", fixture = "regressions")
    fun namedPointerConversionAlias() = checkNative(method = "namedPointerConversionAlias")
}
