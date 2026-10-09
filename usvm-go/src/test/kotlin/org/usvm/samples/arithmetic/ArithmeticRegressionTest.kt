package org.usvm.samples.arithmetic

import org.junit.jupiter.api.Test
import org.usvm.samples.GoNativeTestRunner
import org.usvm.samples.GoResult
import org.usvm.samples.GoSample
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

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

    @Test
    @GoSample(method = "namedNegation", fixture = "regressions")
    fun namedNegation() = checkNative(method = "namedNegation")

    @Test
    @GoSample(method = "namedComplement", fixture = "regressions")
    fun namedComplement() = checkNative(method = "namedComplement")

    @Test
    @GoSample(method = "namedBooleanNot", fixture = "regressions")
    fun namedBooleanNot() = checkNative(method = "namedBooleanNot")

    @Test
    @GoSample(method = "unsignedToFloat64", fixture = "regressions")
    fun unsignedToFloat64() = checkNative(method = "unsignedToFloat64")

    @Test
    @GoSample(method = "unsignedToFloat32", fixture = "regressions")
    fun unsignedToFloat32() = checkNative(method = "unsignedToFloat32")

    @Test
    @GoSample(method = "floatToInt8", fixture = "regressions")
    fun floatToInt8() = checkNative(method = "floatToInt8")

    @Test
    @GoSample(method = "floatToUint8", fixture = "regressions")
    fun floatToUint8() = checkNative(method = "floatToUint8")

    @Test
    @GoSample(method = "floatToInt16", fixture = "regressions")
    fun floatToInt16() = checkNative(method = "floatToInt16")

    @Test
    @GoSample(method = "floatToUint16", fixture = "regressions")
    fun floatToUint16() = checkNative(method = "floatToUint16")

    @Test
    @GoSample(method = "namedInterfaceAssert", fixture = "regressions")
    fun namedInterfaceAssert() = checkNative(method = "namedInterfaceAssert")

    @Test
    @GoSample(method = "nilScalarAssertion", fixture = "regressions")
    fun nilScalarAssertion() = checkNative(method = "nilScalarAssertion")

    @Test
    @GoSample(method = "failedNamedAssertionZero", fixture = "regressions")
    fun failedNamedAssertionZero() = checkNative(method = "failedNamedAssertionZero")

    @Test
    @GoSample(method = "symbolicNamedNegation", fixture = "regressions")
    fun symbolicNamedNegation() {
        checkDiscoveredProperties(
            method = "symbolicNamedNegation",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { value: Number, r: GoResult -> r.long == -value.toLong() },
        )
    }

    @Test
    @GoSample(method = "symbolicNamedComplement", fixture = "regressions")
    fun symbolicNamedComplement() {
        checkDiscoveredProperties(
            method = "symbolicNamedComplement",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { value: UByte, r: GoResult -> r.isSuccess && r.value == value.inv() },
        )
    }

    @Test
    @GoSample(method = "symbolicNamedIdentity", fixture = "regressions")
    fun symbolicNamedIdentity() {
        checkDiscoveredProperties(
            method = "symbolicNamedIdentity",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { value: Number, r: GoResult -> r.long == value.toLong() },
        )
    }

    @Test
    @GoSample(method = "symbolicNamedInterfaceRoundTrip", fixture = "regressions")
    fun symbolicNamedInterfaceRoundTrip() {
        checkDiscoveredProperties(
            method = "symbolicNamedInterfaceRoundTrip",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { value: Number, r: GoResult -> value.toLong() == 0L && r.long == 0L },
            { value: Number, r: GoResult -> value.toLong() != 0L && r.long == value.toLong() },
        )

        checkParameterMutations(
            method = "symbolicNamedInterfaceRoundTrip",
            { execution ->
                execution.argumentsAfter == execution.arguments &&
                    execution.result.long == (execution.arguments.single() as Number).toLong()
            },
        )

        replayInputs(methodName = "symbolicNamedInterfaceRoundTrip", expectedExecutions = 2)
    }
}
