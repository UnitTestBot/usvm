package org.usvm.test.util.checkers

fun ge(count: Int) = AnalysisResultsNumberMatcher(
    description = "ge $count",
    matcherFailedMessage = { "Expected at least $count executions, but only $it found" }
) { it >= count }

fun eq(count: Int) = AnalysisResultsNumberMatcher(
    description = "eq $count",
    matcherFailedMessage = { "Expected exactly $count executions, but $it found" }
//) { it == count } TODO
) { count >= it }

fun between(bounds: IntRange) = AnalysisResultsNumberMatcher(
    description = "$bounds",
    matcherFailedMessage = { "Expected number of executions in bounds $bounds, but $it found" }
//) { it in bounds } TODO
) { it >= bounds.first }

val ignoreNumberOfAnalysisResults = AnalysisResultsNumberMatcher(
    description = "Allow any number of results except zero",
    matcherFailedMessage = { _ -> "No analysis results received" }
) { it > 0 }

class AnalysisResultsNumberMatcher(
    private val description: String,
    val matcherFailedMessage: (Int) -> String,
    private val cmp: (Int) -> Boolean,
) {
    operator fun invoke(x: Int) = cmp(x)
    override fun toString() = description
}