package org.usvm.samples.strings

import org.junit.jupiter.api.Test
import org.usvm.samples.GoMethodTestRunner
import org.usvm.samples.GoResult
import org.usvm.samples.GoSample
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class StringConstraintsTest : GoMethodTestRunner() {
    @Test
    @GoSample(method = "checkGoodString")
    fun checkGoodString() {
        checkDiscoveredProperties(
            method = "checkGoodString",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { s: String, r: GoResult -> s.toByteArray().size < 3 && r.isPanic && r.panicValue == "string too small" },
            { s: String, r: GoResult ->
                s.toByteArray().size >= 3 &&
                    s.any { it !in 'a'..'z' } &&
                    r.isPanic &&
                    r.panicValue == "bad char"
            },
            { s: String, r: GoResult -> s.toByteArray().size >= 3 && s.all { it in 'a'..'z' } && r.isSuccess },
        )
    }

    @Test
    @GoSample(method = "stringGetByte")
    fun stringGetByte() {
        checkDiscoveredProperties(
            method = "stringGetByte",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { s: String, i: Number, r: GoResult ->
                s.toByteArray().size < 3 &&
                    r.isPanic &&
                    r.panicValue == "string too small"
            },
            { s: String, i: Number, r: GoResult ->
                s.toByteArray().size >= 3 &&
                    s.any { it !in 'a'..'z' } &&
                    r.isPanic &&
                    r.panicValue == "bad char"
            },
            { s: String, i: Number, r: GoResult ->
                s.length >= 3 &&
                    s.all { it in 'a'..'z' } &&
                    i.toLong() !in 0..<s.length.toLong() &&
                    r.isPanic
            },
            { s: String, i: Number, r: GoResult ->
                s.length >= 3 &&
                    s.all { it in 'a'..'z' } &&
                    i.toLong() in 0..<s.length.toLong() &&
                    r.isSuccess &&
                    r.value == s[i.toInt()].code.toUByte()
            },
        )
    }

    @Test
    @GoSample(method = "stringGetRune")
    fun stringGetRune() {
        checkDiscoveredProperties(
            method = "stringGetRune",
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            { s: String, i: Number, r: GoResult ->
                s.toByteArray().size < 3 &&
                    r.isPanic &&
                    r.panicValue == "string too small"
            },
            { s: String, i: Number, r: GoResult ->
                s.toByteArray().size >= 3 &&
                    s.any { it !in 'a'..'z' } &&
                    r.isPanic &&
                    r.panicValue == "bad char"
            },
            { s: String, i: Number, r: GoResult ->
                s.length >= 3 &&
                    s.all { it in 'a'..'z' } &&
                    i.toLong() !in 0..<s.length.toLong() &&
                    r.isPanic
            },
            { s: String, i: Number, r: GoResult ->
                s.length >= 3 &&
                    s.all { it in 'a'..'z' } &&
                    i.toLong() in 0..<s.length.toLong() &&
                    r.isSuccess &&
                    r.value == s[i.toInt()]
            },
        )
    }
}
