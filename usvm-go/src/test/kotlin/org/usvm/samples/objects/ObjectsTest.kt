package org.usvm.samples.objects

import org.junit.jupiter.api.Test
import org.usvm.interpreter.GoPointer
import org.usvm.samples.GoMethodTestRunner
import org.usvm.samples.GoResult
import org.usvm.samples.GoSample
import org.usvm.samples.field
import org.usvm.samples.longValue
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class ObjectsTest : GoMethodTestRunner() {
    @Test
    @GoSample(method = "(*usvm/examples.Object).Get")
    fun objectGet() {
        checkDiscoveredProperties(
            method = "(*usvm/examples.Object).Get",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { p: GoPointer?, r: GoResult -> p == null && r.isPanic },
            { p: GoPointer?, r: GoResult ->
                p != null &&
                    (p.value as? Map<*, *>).field(index = 0).longValue() == 0L &&
                    r.long == -1L
            },
            { p: GoPointer?, r: GoResult ->
                p != null &&
                    (p.value as? Map<*, *>).field(index = 0).longValue() != 0L &&
                    r.long == (p.value as? Map<*, *>).field(index = 0).longValue()
            },
        )
    }

    @Test
    @GoSample(method = "(*usvm/examples.Object).Set")
    fun objectSet() {
        checkParameterMutations(
            method = "(*usvm/examples.Object).Set",
            { execution -> execution.arguments[0] == null && execution.result.isPanic },
            { execution ->
                val before = execution.arguments[0] as? GoPointer
                val after = execution.argumentsAfter[0] as? GoPointer
                val value = execution.arguments[1].longValue()
                before != null && execution.result.isSuccess &&
                    (after?.value as? Map<*, *>).field(index = 0).longValue() == value
            },
        )
    }

    @Test
    @GoSample(method = "(*usvm/examples.Object).SetAndReturn")
    fun objectSetAndReturn() {
        checkDiscoveredProperties(
            method = "(*usvm/examples.Object).SetAndReturn",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { p: GoPointer?, _: Number, r: GoResult -> p == null && r.long == -1001L },
            { p: GoPointer?, i: Number, r: GoResult ->
                p != null &&
                    i.toLong() == (p.value as? Map<*, *>).field(index = 0).longValue() &&
                    r.long == 1001L
            },
            { p: GoPointer?, i: Number, r: GoResult ->
                p != null &&
                    i.toLong() != (p.value as? Map<*, *>).field(index = 0).longValue() &&
                    r.long == i.toLong()
            },
        )
    }

    @Test
    @GoSample(method = "ModifyAndGet")
    fun modifyAndGet() {
        checkDiscoveredProperties(
            method = "ModifyAndGet",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { p: GoPointer?, _: Number, r: GoResult -> p == null && r.long == -1001L },
            { p: GoPointer?, i: Number, r: GoResult ->
                p != null &&
                    i.toLong() == (p.value as? Map<*, *>).field(index = 0).longValue() &&
                    r.long == 1001L
            },
            { p: GoPointer?, i: Number, r: GoResult ->
                p != null &&
                    i.toLong() != (p.value as? Map<*, *>).field(index = 0).longValue() &&
                    r.long == i.toLong()
            },
        )
    }
}
