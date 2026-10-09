package org.usvm.samples.types

import org.junit.jupiter.api.Test
import org.usvm.interpreter.GoInterfaceValue
import org.usvm.interpreter.GoPointer
import org.usvm.samples.GoNativeTestRunner
import org.usvm.samples.GoResult
import org.usvm.samples.GoSample
import org.usvm.samples.GoStruct
import org.usvm.samples.field
import org.usvm.samples.longValue
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class InterfaceRegressionTest : GoNativeTestRunner() {
    @Test
    @GoSample(method = "interfaceAssertion", fixture = "regressions")
    fun interfaceAssertion() = checkNative(method = "interfaceAssertion")

    @Test
    @GoSample(method = "interfaceAssertionNoComma", fixture = "regressions")
    fun interfaceAssertionNoComma() = checkNative(method = "interfaceAssertionNoComma")

    @Test
    @GoSample(method = "pointerInterfaceCall", fixture = "regressions")
    fun pointerInterfaceCall() = checkNative(method = "pointerInterfaceCall")

    @Test
    @GoSample(method = "valueHasPointerMethods", fixture = "regressions")
    fun valueHasPointerMethods() = checkNative(method = "valueHasPointerMethods")

    @Test
    @GoSample(method = "(usvm/regressions.reviewError).Error", fixture = "regressions")
    fun valueReceiverReturnsItsString() {
        checkDiscoveredProperties(
            method = "(usvm/regressions.reviewError).Error",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { value: String, result: GoResult -> result.isSuccess && result.value == value },
        )
    }

    @Test
    @GoSample(method = "(*usvm/regressions.reviewCounter).Read", fixture = "regressions")
    fun pointerReceiverReadsItsField() {
        checkDiscoveredProperties(
            method = "(*usvm/regressions.reviewCounter).Read",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { counter: GoPointer?, result: GoResult -> counter == null && result.isPanic },
            { counter: GoPointer?, result: GoResult ->
                counter != null && result.long == (counter.value as GoStruct).field(index = 0).longValue()
            },
        )
    }

    @Test
    @GoSample(method = "symbolicInterfaceReceiver", fixture = "regressions")
    fun interfaceInputsUseThePointerMethodSet() {
        checkDiscoveredProperties(
            method = "symbolicInterfaceReceiver",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { reader: GoInterfaceValue?, result: GoResult -> reader == null && result.isPanic },
            { reader: GoInterfaceValue?, result: GoResult ->
                reader != null && reader.value == null && result.isPanic
            },
            { reader: GoInterfaceValue?, result: GoResult ->
                val pointer = reader?.value as? GoPointer
                pointer != null && result.long == (pointer.value as GoStruct).field(index = 0).longValue()
            },
        )
    }
}
