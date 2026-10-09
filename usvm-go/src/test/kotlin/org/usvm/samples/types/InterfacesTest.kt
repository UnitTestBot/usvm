package org.usvm.samples.types

import org.jacodb.go.api.PointerType
import org.junit.jupiter.api.Test
import org.usvm.interpreter.GoInterfaceValue
import org.usvm.interpreter.GoPointer
import org.usvm.samples.GoMethodTestRunner
import org.usvm.samples.GoResult
import org.usvm.samples.GoSample
import org.usvm.samples.field
import org.usvm.samples.isNamed
import org.usvm.samples.longValue
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class InterfacesTest : GoMethodTestRunner() {
    @Test
    @GoSample(method = "assertCreature")
    fun assertCreature() {
        checkDiscoveredProperties(
            method = "assertCreature",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { r: GoResult -> r.list == listOf(mapOf("field0" to "Name", "field1" to 42L), true) },
        )
    }

    @Test
    @GoSample(method = "assertCreatureNoComma")
    fun assertCreatureNoComma() {
        checkDiscoveredProperties(
            method = "assertCreatureNoComma",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { r: GoResult -> r.map == mapOf("field0" to "Name", "field1" to 42L) },
        )
    }

    @Test
    @GoSample(method = "assertCreaturePointer")
    fun assertCreaturePointer() {
        checkDiscoveredProperties(
            method = "assertCreaturePointer",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { r: GoResult -> r.list == listOf(GoPointer(mapOf("field0" to "Name", "field1" to 42L)), true) },
        )
    }

    @Test
    @GoSample(method = "assertCreatureFailNoComma")
    fun assertCreatureFailNoComma() {
        // The return after this guaranteed failing assertion is unreachable in Go.
        machineOptions = machineOptions.copy(failOnNotFullCoverage = false)

        checkDiscoveredProperties(
            method = "assertCreatureFailNoComma",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { r: GoResult -> r.isPanic },
        )
    }

    @Test
    @GoSample(method = "assertIntAny")
    fun assertIntAny() {
        checkDiscoveredProperties(
            method = "assertIntAny",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { r: GoResult -> r.long == 5L },
        )
    }

    @Test
    @GoSample(method = "callCreature")
    fun callCreature() {
        checkDiscoveredProperties(
            method = "callCreature",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { c: GoInterfaceValue?, r: GoResult -> c == null && r.long == -1L },
            { c: GoInterfaceValue?, r: GoResult -> c?.type is PointerType && c.value == null && r.isPanic },
            { c: GoInterfaceValue?, r: GoResult ->
                val receiver = when (val value = c?.value) {
                    is GoPointer -> value.value as? Map<*, *>
                    is Map<*, *> -> value
                    else -> null
                }
                c != null && receiver != null && r.long == receiver.field(index = 1).longValue()
            },
        )
    }

    @Test
    @GoSample(method = "assertCreatureArgument")
    fun assertCreatureArgument() {
        checkDiscoveredProperties(
            method = "assertCreatureArgument",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { c: GoInterfaceValue?, r: GoResult ->
                c?.isNamed(name = "usvm/examples.Person") == true &&
                    r.list == listOf(c.value, true)
            },
            { c: GoInterfaceValue?, r: GoResult ->
                c?.isNamed(name = "usvm/examples.Person") != true &&
                    r.list == listOf(mapOf("field0" to "", "field1" to 0L), false)
            },
        )
    }

    @Test
    @GoSample(method = "assertCreatureArgumentCall")
    fun assertCreatureArgumentCall() {
        checkDiscoveredProperties(
            method = "assertCreatureArgumentCall",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { c: GoInterfaceValue?, r: GoResult ->
                c?.isNamed(name = "usvm/examples.Person") == true &&
                    r.long == (c.value as? Map<*, *>).field(index = 1).longValue()
            },
            { c: GoInterfaceValue?, r: GoResult ->
                c?.isNamed(name = "usvm/examples.Person") != true &&
                    r.long == -1L
            },
        )
    }
}
