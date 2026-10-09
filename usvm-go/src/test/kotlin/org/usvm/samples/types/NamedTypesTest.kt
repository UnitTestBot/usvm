package org.usvm.samples.types

import org.junit.jupiter.api.Test
import org.usvm.interpreter.GoInterfaceValue
import org.usvm.samples.GoMethodTestRunner
import org.usvm.samples.GoResult
import org.usvm.samples.GoSample
import org.usvm.samples.isNamed
import org.usvm.samples.longValue
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.type.GoBasicTypes

class NamedTypesTest : GoMethodTestRunner() {
    @Test
    @GoSample(method = "(usvm/examples.NamedInt).square")
    fun namedIntsquare() {
        checkDiscoveredProperties(
            method = "(usvm/examples.NamedInt).square",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { n: Number, r: GoResult -> n.toLong() == 0L && r.isPanic && r.panicValue == "zero" },
            { n: Number, r: GoResult -> n.toLong() in listOf(-1L, 1L) && r.isPanic && r.panicValue == "one" },
            { n: Number, r: GoResult -> n.toLong() !in -1..1 && r.long == nativeInt(n.toLong() * n.toLong()) },
        )
    }

    @Test
    @GoSample(method = "callNamedInt")
    fun callNamedInt() {
        checkDiscoveredProperties(
            method = "callNamedInt",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { n: Number, r: GoResult -> n.toLong() == 0L && r.isPanic && r.panicValue == "zero" },
            { n: Number, r: GoResult -> n.toLong() in listOf(-1L, 1L) && r.isPanic && r.panicValue == "one" },
            { n: Number, r: GoResult -> n.toLong() !in -1..1 && r.long == nativeInt(n.toLong() * n.toLong()) },
        )
    }

    @Test
    @GoSample(method = "toNamedInt")
    fun toNamedInt() {
        checkDiscoveredProperties(
            method = "toNamedInt",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { n: Number, r: GoResult -> r.long == nativeInt(n.toLong() + 1) },
        )
    }

    @Test
    @GoSample(method = "assertNamedIntCall")
    fun assertNamedIntCall() {
        checkDiscoveredProperties(
            method = "assertNamedIntCall",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { n: GoInterfaceValue?, r: GoResult ->
                n?.isNamed(name = "usvm/examples.NamedInt") == true &&
                    n.value.longValue() in -1..1 &&
                    r.isPanic
            },
            { n: GoInterfaceValue?, r: GoResult ->
                n?.isNamed(name = "usvm/examples.NamedInt") == true &&
                    n.value.longValue() !in -1..1 &&
                    r.long == nativeInt(n.value.longValue() * n.value.longValue())
            },
            { n: GoInterfaceValue?, r: GoResult ->
                n?.type == GoBasicTypes.INT &&
                    nativeInt(n.value.longValue() * n.value.longValue()) <= 1 &&
                    r.isPanic
            },
            { n: GoInterfaceValue?, r: GoResult ->
                n?.type == GoBasicTypes.INT &&
                    nativeInt(n.value.longValue() * n.value.longValue()) > 1 &&
                    r.long == nativeInt(n.value.longValue() * n.value.longValue())
            },
            { n: GoInterfaceValue?, r: GoResult ->
                n?.isNamed(name = "usvm/examples.NamedInt") != true &&
                    n?.type != GoBasicTypes.INT &&
                    r.long == -1L
            },
        )
    }
}
