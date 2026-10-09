package org.usvm.samples

import org.usvm.CoverageZone
import org.usvm.GoMachine
import org.usvm.GoMachineOptions
import org.usvm.GoPackage
import org.usvm.GoProgram
import org.usvm.PathSelectionStrategy
import org.usvm.StateCollectionStrategy
import org.usvm.UMachineOptions
import org.usvm.generatedGoFile
import org.usvm.interpreter.GoInterfaceValue
import org.usvm.interpreter.SuccessfulExecutionResult
import org.usvm.interpreter.UnsuccessfulExecutionResult
import org.usvm.model.Converter
import org.usvm.model.Parser
import org.usvm.test.util.TestRunner
import org.usvm.test.util.checkers.AnalysisResultsNumberMatcher
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf
import kotlin.time.Duration.Companion.seconds

open class GoMethodTestRunner(
    private val fixture: String = "examples",
) : TestRunner<GoExecution, String, KClass<*>?, Unit>() {
    protected val pkg: GoPackage by lazy {
        Converter.unpack(Parser().deserialize(generatedGoFile("$fixture/usvm_$fixture.json").path))
    }

    override var options = UMachineOptions(
        pathSelectionStrategies = listOf(PathSelectionStrategy.FORK_DEPTH),
        stateCollectionStrategy = StateCollectionStrategy.ALL,
        coverageZone = CoverageZone.TRANSITIVE,
        exceptionsPropagation = true,
        stopOnCoverage = 0,
        collectedStatesLimit = 100,
        timeout = 5.seconds,
        solverTimeout = 2.seconds,
        typeOperationsTimeout = 2.seconds,
    )

    protected var machineOptions = GoMachineOptions(failOnNotFullCoverage = true, uncoveredMethods = emptyList())

    protected fun checkParameterMutations(method: String, vararg expectations: (GoExecution) -> Boolean) {
        require(expectations.isNotEmpty()) { "A mutation expectation is required for $method" }
        val specification: (GoExecution) -> Boolean = { execution ->
            expectations.any { runCatching { it(execution) }.getOrDefault(false) }
        }

        internalCheck(
            target = method,
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            analysisResultsMatchers = expectations,
            invariants = arrayOf(specification),
            extractValuesToCheck = { listOf(it) },
            expectedTypesForExtractedValues = arrayOf(GoExecution::class),
            checkMode = CheckMode.MATCH_PROPERTIES,
            coverageChecker = { true },
        )
    }

    protected fun nativeInt(value: Long): Long = if (pkg.intSize == Int.SIZE_BITS) value.toInt().toLong() else value

    protected fun checkNoResults(method: String) {
        internalCheck(
            target = method,
            analysisResultsNumberMatcher = org.usvm.test.util.checkers.noResultsExpected,
            analysisResultsMatchers = emptyArray(),
            invariants = emptyArray(),
            extractValuesToCheck = { it.arguments + it.result },
            expectedTypesForExtractedValues = emptyArray(),
            checkMode = CheckMode.MATCH_PROPERTIES,
            coverageChecker = { true },
        )
    }

    override val typeTransformer: (Any?) -> KClass<*>? = { it?.let { value -> value::class } }
    override val checkType: (KClass<*>?, KClass<*>?) -> Boolean = { expected, actual ->
        expected == null || actual == null || actual.isSubclassOf(expected)
    }
    override val coverageRunner: (List<GoExecution>) -> Unit = { }
    override val runner: (String, UMachineOptions) -> List<GoExecution> = { method, options ->
        GoMachine(GoProgram(listOf(pkg)), options, machineOptions).use { machine ->
            machine.analyzeAndResolve(pkg, method).map { execution ->
                when (execution) {
                    is SuccessfulExecutionResult -> GoExecution(
                        execution.inputModel.arguments,
                        execution.outputModel.argumentsAfter,
                        GoResult(value = execution.outputModel.returnExpr, isPanic = false),
                    )
                    is UnsuccessfulExecutionResult -> GoExecution(
                        execution.inputModel.arguments,
                        execution.argumentsAfter,
                        GoResult(value = execution.panicValue, isPanic = true),
                    )
                }
            }
        }
    }

    protected fun checkDiscoveredProperties(
        method: String,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
        vararg analysisResultsMatchers: (GoResult) -> Boolean,
    ) {
        require(analysisResultsMatchers.isNotEmpty()) { "A semantic expectation is required for $method" }
        val specification: (GoResult) -> Boolean = { result ->
            analysisResultsMatchers.any { matcher -> runCatching { matcher(result) }.getOrDefault(false) }
        }

        internalCheck(
            target = method,
            analysisResultsNumberMatcher = analysisResultsNumberMatcher,
            analysisResultsMatchers = analysisResultsMatchers,
            invariants = arrayOf(specification),
            extractValuesToCheck = { it.arguments + it.result },
            expectedTypesForExtractedValues = arrayOf(GoResult::class),
            checkMode = CheckMode.MATCH_PROPERTIES,
            coverageChecker = { true },
        )
    }

    protected inline fun <reified A0> checkDiscoveredProperties(
        method: String,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
        vararg analysisResultsMatchers: (A0, GoResult) -> Boolean,
    ) {
        require(analysisResultsMatchers.isNotEmpty()) { "A semantic expectation is required for $method" }
        val specification: (A0, GoResult) -> Boolean = { a0, result ->
            analysisResultsMatchers.any { matcher -> runCatching { matcher(a0, result) }.getOrDefault(false) }
        }

        internalCheck(
            target = method,
            analysisResultsNumberMatcher = analysisResultsNumberMatcher,
            analysisResultsMatchers = analysisResultsMatchers,
            invariants = arrayOf(specification),
            extractValuesToCheck = { it.arguments + it.result },
            expectedTypesForExtractedValues = arrayOf(A0::class, GoResult::class),
            checkMode = CheckMode.MATCH_PROPERTIES,
            coverageChecker = { true },
        )
    }

    protected inline fun <reified A0, reified A1> checkDiscoveredProperties(
        method: String,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
        vararg analysisResultsMatchers: (A0, A1, GoResult) -> Boolean,
    ) {
        require(analysisResultsMatchers.isNotEmpty()) { "A semantic expectation is required for $method" }
        val specification: (A0, A1, GoResult) -> Boolean = { a0, a1, result ->
            analysisResultsMatchers.any { matcher -> runCatching { matcher(a0, a1, result) }.getOrDefault(false) }
        }

        internalCheck(
            target = method,
            analysisResultsNumberMatcher = analysisResultsNumberMatcher,
            analysisResultsMatchers = analysisResultsMatchers,
            invariants = arrayOf(specification),
            extractValuesToCheck = { it.arguments + it.result },
            expectedTypesForExtractedValues = arrayOf(A0::class, A1::class, GoResult::class),
            checkMode = CheckMode.MATCH_PROPERTIES,
            coverageChecker = { true },
        )
    }

    protected inline fun <reified A0, reified A1, reified A2> checkDiscoveredProperties(
        method: String,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
        vararg analysisResultsMatchers: (A0, A1, A2, GoResult) -> Boolean,
    ) {
        require(analysisResultsMatchers.isNotEmpty()) { "A semantic expectation is required for $method" }
        val specification: (A0, A1, A2, GoResult) -> Boolean = { a0, a1, a2, result ->
            analysisResultsMatchers.any { matcher -> runCatching { matcher(a0, a1, a2, result) }.getOrDefault(false) }
        }

        internalCheck(
            target = method,
            analysisResultsNumberMatcher = analysisResultsNumberMatcher,
            analysisResultsMatchers = analysisResultsMatchers,
            invariants = arrayOf(specification),
            extractValuesToCheck = { it.arguments + it.result },
            expectedTypesForExtractedValues = arrayOf(A0::class, A1::class, A2::class, GoResult::class),
            checkMode = CheckMode.MATCH_PROPERTIES,
            coverageChecker = { true },
        )
    }

    protected inline fun <reified A0, reified A1, reified A2, reified A3> checkDiscoveredProperties(
        method: String,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
        vararg analysisResultsMatchers: (A0, A1, A2, A3, GoResult) -> Boolean,
    ) {
        require(analysisResultsMatchers.isNotEmpty()) { "A semantic expectation is required for $method" }
        val specification: (A0, A1, A2, A3, GoResult) -> Boolean =
            { a0, a1, a2, a3, result ->
                analysisResultsMatchers.any { matcher ->
                    runCatching { matcher(a0, a1, a2, a3, result) }.getOrDefault(false)
                }
            }

        internalCheck(
            target = method,
            analysisResultsNumberMatcher = analysisResultsNumberMatcher,
            analysisResultsMatchers = analysisResultsMatchers,
            invariants = arrayOf(specification),
            extractValuesToCheck = { it.arguments + it.result },
            expectedTypesForExtractedValues = arrayOf(A0::class, A1::class, A2::class, A3::class, GoResult::class),
            checkMode = CheckMode.MATCH_PROPERTIES,
            coverageChecker = { true },
        )
    }

    protected fun checkMatches(
        method: String,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
        vararg analysisResultsMatchers: (GoResult) -> Boolean,
    ) {
        require(analysisResultsMatchers.isNotEmpty()) { "A semantic expectation is required for $method" }
        val specification: (GoResult) -> Boolean = { result ->
            analysisResultsMatchers.any { matcher -> runCatching { matcher(result) }.getOrDefault(false) }
        }

        internalCheck(
            target = method,
            analysisResultsNumberMatcher = analysisResultsNumberMatcher,
            analysisResultsMatchers = analysisResultsMatchers,
            invariants = arrayOf(specification),
            extractValuesToCheck = { it.arguments + it.result },
            expectedTypesForExtractedValues = arrayOf(GoResult::class),
            checkMode = CheckMode.MATCH_EXECUTIONS,
            coverageChecker = { true },
        )
    }

    protected inline fun <reified A0> checkMatches(
        method: String,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
        vararg analysisResultsMatchers: (A0, GoResult) -> Boolean,
    ) {
        require(analysisResultsMatchers.isNotEmpty()) { "A semantic expectation is required for $method" }
        val specification: (A0, GoResult) -> Boolean = { a0, result ->
            analysisResultsMatchers.any { matcher -> runCatching { matcher(a0, result) }.getOrDefault(false) }
        }

        internalCheck(
            target = method,
            analysisResultsNumberMatcher = analysisResultsNumberMatcher,
            analysisResultsMatchers = analysisResultsMatchers,
            invariants = arrayOf(specification),
            extractValuesToCheck = { it.arguments + it.result },
            expectedTypesForExtractedValues = arrayOf(A0::class, GoResult::class),
            checkMode = CheckMode.MATCH_EXECUTIONS,
            coverageChecker = { true },
        )
    }

    protected inline fun <reified A0, reified A1> checkMatches(
        method: String,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
        vararg analysisResultsMatchers: (A0, A1, GoResult) -> Boolean,
    ) {
        require(analysisResultsMatchers.isNotEmpty()) { "A semantic expectation is required for $method" }
        val specification: (A0, A1, GoResult) -> Boolean = { a0, a1, result ->
            analysisResultsMatchers.any { matcher -> runCatching { matcher(a0, a1, result) }.getOrDefault(false) }
        }

        internalCheck(
            target = method,
            analysisResultsNumberMatcher = analysisResultsNumberMatcher,
            analysisResultsMatchers = analysisResultsMatchers,
            invariants = arrayOf(specification),
            extractValuesToCheck = { it.arguments + it.result },
            expectedTypesForExtractedValues = arrayOf(A0::class, A1::class, GoResult::class),
            checkMode = CheckMode.MATCH_EXECUTIONS,
            coverageChecker = { true },
        )
    }

    protected inline fun <reified A0, reified A1, reified A2> checkMatches(
        method: String,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
        vararg analysisResultsMatchers: (A0, A1, A2, GoResult) -> Boolean,
    ) {
        require(analysisResultsMatchers.isNotEmpty()) { "A semantic expectation is required for $method" }
        val specification: (A0, A1, A2, GoResult) -> Boolean = { a0, a1, a2, result ->
            analysisResultsMatchers.any { matcher -> runCatching { matcher(a0, a1, a2, result) }.getOrDefault(false) }
        }

        internalCheck(
            target = method,
            analysisResultsNumberMatcher = analysisResultsNumberMatcher,
            analysisResultsMatchers = analysisResultsMatchers,
            invariants = arrayOf(specification),
            extractValuesToCheck = { it.arguments + it.result },
            expectedTypesForExtractedValues = arrayOf(A0::class, A1::class, A2::class, GoResult::class),
            checkMode = CheckMode.MATCH_EXECUTIONS,
            coverageChecker = { true },
        )
    }

    protected inline fun <reified A0, reified A1, reified A2, reified A3> checkMatches(
        method: String,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
        vararg analysisResultsMatchers: (A0, A1, A2, A3, GoResult) -> Boolean,
    ) {
        require(analysisResultsMatchers.isNotEmpty()) { "A semantic expectation is required for $method" }
        val specification: (A0, A1, A2, A3, GoResult) -> Boolean =
            { a0, a1, a2, a3, result ->
                analysisResultsMatchers.any { matcher ->
                    runCatching { matcher(a0, a1, a2, a3, result) }.getOrDefault(false)
                }
            }

        internalCheck(
            target = method,
            analysisResultsNumberMatcher = analysisResultsNumberMatcher,
            analysisResultsMatchers = analysisResultsMatchers,
            invariants = arrayOf(specification),
            extractValuesToCheck = { it.arguments + it.result },
            expectedTypesForExtractedValues = arrayOf(A0::class, A1::class, A2::class, A3::class, GoResult::class),
            checkMode = CheckMode.MATCH_EXECUTIONS,
            coverageChecker = { true },
        )
    }
}

data class GoExecution(val arguments: List<Any?>, val argumentsAfter: List<Any?>, val result: GoResult)

data class GoResult(val value: Any?, val isPanic: Boolean) {
    val isSuccess: Boolean get() = !isPanic
    val panicValue: Any? get() = if (isPanic) (value as? GoInterfaceValue)?.value ?: value else null
    val long: Long? get() = if (isSuccess) (value as? Number)?.toLong() else null
    val list: List<*>? get() = if (isSuccess) value as? List<*> else null
    val map: Map<*, *>? get() = if (isSuccess) value as? Map<*, *> else null
}

typealias GoSlice = List<*>?
typealias GoMap = Map<*, *>?
typealias GoStruct = Map<*, *>?

fun Any?.longValue(): Long = (this as Number).toLong()
fun GoSlice.longValues(): List<Long> = orEmpty().map { it.longValue() }
fun GoMap.longEntry(key: Long): Long = orEmpty().entries.firstOrNull {
    it.key.longValue() == key
}?.value?.longValue() ?: 0
fun GoStruct.field(index: Int): Any? = this?.get("field$index")
