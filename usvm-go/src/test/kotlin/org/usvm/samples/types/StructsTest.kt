package org.usvm.samples.types

import org.junit.jupiter.api.Test
import org.usvm.interpreter.GoInterfaceValue
import org.usvm.samples.GoMethodTestRunner
import org.usvm.samples.GoResult
import org.usvm.samples.GoSample
import org.usvm.samples.GoStruct
import org.usvm.samples.field
import org.usvm.samples.longValue
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class StructsTest : GoMethodTestRunner() {
    @Test
    @GoSample(method = "GetAge")
    fun getAge() {
        checkDiscoveredProperties(
            method = "GetAge",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { p: GoStruct, r: GoResult -> r.long == p.field(index = 1).longValue() },
        )
    }

    @Test
    @GoSample(method = "(usvm/examples.Person).GetAge")
    fun personGetAge() {
        checkDiscoveredProperties(
            method = "(usvm/examples.Person).GetAge",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { p: GoStruct, r: GoResult -> r.long == p.field(index = 1).longValue() },
        )
    }

    @Test
    @GoSample(method = "(usvm/examples.Person).GetName")
    fun personGetName() {
        checkDiscoveredProperties(
            method = "(usvm/examples.Person).GetName",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { p: GoStruct, r: GoResult -> r.isSuccess && r.value == p.field(index = 0) },
        )
    }

    @Test
    @GoSample(method = "(usvm/examples.Person).Validate")
    fun personValidate() {
        checkDiscoveredProperties(
            method = "(usvm/examples.Person).Validate",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { _: GoStruct, r: GoResult -> r.list == listOf(true, null) },
        )
    }

    @Test
    @GoSample(method = "(usvm/examples.Person).WithName")
    fun personWithName() {
        checkDiscoveredProperties(
            method = "(usvm/examples.Person).WithName",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { _: GoStruct, name: String, r: GoResult ->
                name.toByteArray().size < 3 &&
                    r.isPanic &&
                    r.panicValue == "string too small"
            },
            { _: GoStruct, name: String, r: GoResult ->
                name.toByteArray().size >= 3 &&
                    name.any { it !in 'a'..'z' } &&
                    r.isPanic &&
                    r.panicValue == "bad char"
            },
            { p: GoStruct, name: String, r: GoResult ->
                name.length >= 3 &&
                    name.all { it in 'a'..'z' } &&
                    r.isSuccess &&
                    (r.value as? GoInterfaceValue)?.value == mapOf("field0" to name, "field1" to p.field(index = 1))
            },
        )
    }

    @Test
    @GoSample(method = "nameSmall")
    fun nameSmall() {
        checkDiscoveredProperties(
            method = "nameSmall",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { p: GoStruct, r: GoResult -> (p.field(index = 0) as String).toByteArray().size < 3 && r.isPanic },
            { p: GoStruct, r: GoResult ->
                (p.field(index = 0) as String).length >= 3 &&
                    (p.field(index = 0) as String).any { it !in 'a'..'z' } &&
                    r.isPanic
            },
            { p: GoStruct, r: GoResult ->
                (p.field(index = 0) as String).length >= 3 &&
                    (p.field(index = 0) as String).all { it in 'a'..'z' } &&
                    r.isSuccess &&
                    r.value == ((p.field(index = 0) as String).length < 5)
            },
        )
    }
}
