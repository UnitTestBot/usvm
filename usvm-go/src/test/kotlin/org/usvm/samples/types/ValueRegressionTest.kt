package org.usvm.samples.types

import org.junit.jupiter.api.Test
import org.usvm.samples.GoNativeTestRunner
import org.usvm.samples.GoSample

class ValueRegressionTest : GoNativeTestRunner() {
    @Test
    @GoSample(method = "structValueCopy", fixture = "regressions")
    fun structValueCopy() = checkNative(method = "structValueCopy")

    @Test
    @GoSample(method = "nestedStructValueCopy", fixture = "regressions")
    fun nestedStructValueCopy() = checkNative(method = "nestedStructValueCopy")

    @Test
    @GoSample(method = "arrayValueCopy", fixture = "regressions")
    fun arrayValueCopy() = checkNative(method = "arrayValueCopy")

    @Test
    @GoSample(method = "structArgumentCopy", fixture = "regressions")
    fun structArgumentCopy() = checkNative(method = "structArgumentCopy")

    @Test
    @GoSample(method = "arrayArgumentCopy", fixture = "regressions")
    fun arrayArgumentCopy() = checkNative(method = "arrayArgumentCopy")

    @Test
    @GoSample(method = "interfaceStructCopy", fixture = "regressions")
    fun interfaceStructCopy() = checkNative(method = "interfaceStructCopy")

    @Test
    @GoSample(method = "nilStructAssertionZero", fixture = "regressions")
    fun nilStructAssertionZero() = checkNative(method = "nilStructAssertionZero")

    @Test
    @GoSample(method = "typedNilPointerAssertion", fixture = "regressions")
    fun typedNilPointerAssertion() = checkNative(method = "typedNilPointerAssertion")

    @Test
    @GoSample(method = "nilStructAssertionOk", fixture = "regressions")
    fun nilStructAssertionOk() = checkNative(method = "nilStructAssertionOk")

    @Test
    @GoSample(method = "pointerToInterfaceDoesNotImplement", fixture = "regressions")
    fun pointerToInterfaceDoesNotImplement() = checkNative(method = "pointerToInterfaceDoesNotImplement")
}
