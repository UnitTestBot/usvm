package org.usvm.samples.arithmetic

import org.junit.jupiter.api.Test
import org.usvm.samples.GoNativeTestRunner
import org.usvm.samples.GoSample

class ArithmeticRegressionTest : GoNativeTestRunner() {
    @Test
    @GoSample(method = "bitwiseAndNot", fixture = "regressions")
    fun bitwiseAndNot() = checkNative(method = "bitwiseAndNot")

    @Test
    @GoSample(method = "bitwiseAndOr", fixture = "regressions")
    fun bitwiseAndOr() = checkNative(method = "bitwiseAndOr")

    @Test
    @GoSample(method = "bitwiseComplement", fixture = "regressions")
    fun bitwiseComplement() = checkNative(method = "bitwiseComplement")

    @Test
    @GoSample(method = "divideByZero", fixture = "regressions")
    fun divideByZero() = checkNative(method = "divideByZero")

    @Test
    @GoSample(method = "nativeIntOverflow", fixture = "regressions")
    fun nativeIntOverflow() = checkNative(method = "nativeIntOverflow")

    @Test
    @GoSample(method = "nativeIntWidth", fixture = "regressions")
    fun nativeIntWidth() = checkNative(method = "nativeIntWidth")

    @Test
    @GoSample(method = "negativeShift", fixture = "regressions")
    fun negativeShift() = checkNative(method = "negativeShift")

    @Test
    @GoSample(method = "oversizedShiftCount", fixture = "regressions")
    fun oversizedShiftCount() = checkNative(method = "oversizedShiftCount")

    @Test
    @GoSample(method = "remainderByZero", fixture = "regressions")
    fun remainderByZero() = checkNative(method = "remainderByZero")

    @Test
    @GoSample(method = "shiftByBitWidth", fixture = "regressions")
    fun shiftByBitWidth() = checkNative(method = "shiftByBitWidth")

    @Test
    @GoSample(method = "signedRightShift", fixture = "regressions")
    fun signedRightShift() = checkNative(method = "signedRightShift")

    @Test
    @GoSample(method = "unsignedResultWidth", fixture = "regressions")
    fun unsignedResultWidth() = checkNative(method = "unsignedResultWidth")

    @Test
    @GoSample(method = "unsignedRightShift", fixture = "regressions")
    fun unsignedRightShift() = checkNative(method = "unsignedRightShift")

    @Test
    @GoSample(method = "unsignedWidening", fixture = "regressions")
    fun unsignedWidening() = checkNative(method = "unsignedWidening")
}
