package org.usvm.test.util

import mu.KLogging
import org.usvm.UMachineOptions
import org.usvm.test.util.TestRunner.CheckMode.MATCH_EXECUTIONS
import org.usvm.test.util.TestRunner.CheckMode.MATCH_PROPERTIES
import org.usvm.test.util.checkers.AnalysisResultsNumberMatcher
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

val logger = object : KLogging() {}.logger

/**
 * A base class for test runners for all interpreters.
 *
 * It generalized by four type arguments:
 * * [AnalysisResult] is a result produced for a single execution branch in CFG;
 * * [Target] is what an interpreter will be run on;
 * * [Type] is a type using in the interpreter;
 * * [Coverage] is a class representing coverage statistics for the target language of the interpreter.
 *
 * It also takes three arguments in the constructor:
 * * [typeTransformer] extracts a [Type] for any object;
 * * [runner] runs the interpreter and returns a list of [AnalysisResult];
 * * [coverageRunner] calculates coverage (of any kind) of the list of [AnalysisResult].
 *
 */
abstract class TestRunner<AnalysisResult, Target, Type, Coverage> {
    abstract val typeTransformer: (Any?) -> Type
    abstract val checkType: (Type, Type) -> Boolean
    abstract val runner: (Target, UMachineOptions) -> List<AnalysisResult>
    abstract val coverageRunner: (List<AnalysisResult>) -> Coverage

    abstract var options: UMachineOptions

    /**
     * Parametrizes [runner] with given options and executes [action].
     */
    protected fun <T> withOptions(options: UMachineOptions, action: () -> T): T {
        val prevOptions = this.options
        try {
            this.options = options
            return action()
        } finally {
            this.options = prevOptions
        }
    }

    /**
     * Runs an interpreter on the [target], after that makes several checks of the results it got:
     * * whether the interpreter produces as many results as we expected using [analysisResultsNumberMatcher];
     * * whether all [analysisResultsMatchers] are satisfied with respect to the [checkMode];
     * * whether all [invariants] are satisfied in all the analysis results;
     * * whether all types in the results matches with the expected ones ([expectedTypesForExtractedValues]);
     * * whether we got an expected coverage result ([coverageChecker]).
     *
     * During the process, it extracts values required for
     * checks from the [AnalysisResult] using [extractValuesToCheck].
     */
    protected fun internalCheck(
        target: Target,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher,
        analysisResultsMatchers: Array<out Function<Boolean>>,
        invariants: Array<out Function<Boolean>>,
        extractValuesToCheck: (AnalysisResult) -> List<Any?>,
        expectedTypesForExtractedValues: Array<out Type>,
        checkMode: CheckMode,
        coverageChecker: (Coverage) -> Boolean,
    ) {
        val analysisResults = runWithTimout(2.minutes) {
            runner(target, options)
        }

        logger.debug { options }

        logger.info {
            buildString {
                appendLine("${analysisResults.size} executions were found:")
                analysisResults.forEach { appendLine("\t${it.safeToString()}") }
            }
        }

        val valuesToCheck = runWithTimout(10.seconds) {
            analysisResults.map { extractValuesToCheck(it) }
        }

        logger.info {
            buildString {
                appendLine("Extracted values:")
                valuesToCheck.forEach { appendLine("\t${it.safeToString()}") }
            }
        }

        runWithTimout(10.seconds) {
            checkTypes(expectedTypesForExtractedValues, valuesToCheck)
            checkInvariant(invariants, valuesToCheck)
        }

        // TODO should I add a comparison between real run and symbolic one?

        when (checkMode) {
            MATCH_EXECUTIONS -> runWithTimout(10.seconds) {
                matchExecutions(valuesToCheck, analysisResultsMatchers)
            }
            MATCH_PROPERTIES -> {
                runWithTimout(10.seconds) {
                    checkDiscoveredProperties(valuesToCheck, analysisResultsMatchers)
                }
                require(analysisResultsNumberMatcher(analysisResults.size)) {
                    analysisResultsNumberMatcher.matcherFailedMessage(analysisResults.size)
                }
            }
        }

        val coverageResult = coverageRunner(analysisResults)

        require(coverageChecker(coverageResult)) {
            "Coverage check failed: $coverageChecker, result: $coverageResult"
        }
    }

    private fun checkInvariant(
        invariants: Array<out Function<Boolean>>,
        valuesToCheck: List<List<Any?>>,
    ) {
        val violatedInvariants = mutableListOf<Pair<Int, List<Int>>>()
        val indexedInvariants = invariants.withIndex()

        valuesToCheck.withIndex().forEach { (valuesIndex, params) ->
            val tmpViolatedInvariants = mutableListOf<Int>()

            indexedInvariants.forEach { (invariantIndex, invariant) ->
                val result = invokeFunction(invariant, params)
                if (!result) tmpViolatedInvariants += invariantIndex
            }

            if (tmpViolatedInvariants.isNotEmpty()) {
                violatedInvariants += valuesIndex to tmpViolatedInvariants
            }
        }

        require(violatedInvariants.isEmpty()) {
            "Some executions violated invariants:" + System.lineSeparator() +
                    violatedInvariants.joinToString(System.lineSeparator()) { (executionIndex, invariantsIndices) ->
                        "Index: ${executionIndex}, invariants: ${invariantsIndices}}"
                    }
        }
    }

    private fun checkTypes(
        argumentsTypes: Array<out Type>,
        valuesToCheck: List<List<Any?>>,
    ) {
        for (valueToCheck in valuesToCheck) {
            val actualArgsTypes = valueToCheck.map { typeTransformer(it) }
            val expectedTypesWithActual = argumentsTypes.zip(actualArgsTypes)

            val mismatchedTypes = expectedTypesWithActual
                .withIndex()
                .filterNot { checkType(it.value.first, it.value.second) }

            check(mismatchedTypes.isEmpty()) {
                "Some types don't match at positions (from 0): ${mismatchedTypes.map { it.index }}. ${System.lineSeparator()}" +
                        "Type pairs (index: Expected -> Found): " + mismatchedTypes.joinToString(
                    prefix = System.lineSeparator(),
                    separator = System.lineSeparator()
                ) { (index, value) ->
                    "\tAt index $index: ${value.first} -> ${value.second}"
                }
            }
        }
    }

    private fun checkDiscoveredProperties(
        valuesToCheck: List<List<Any?>>,
        propertiesToDiscover: Array<out Function<Boolean>>,
    ) {
        check(
            valuesToCheck,
            propertiesToDiscover,
            successCriteria = { array -> array.all { it > 0 } },
            errorMessage = { array ->
                val unsatisfiedPositions = array.withIndex().filter { it.value == 0 }.map { it.index }

                "Some properties were not discovered at positions (from 0): $unsatisfiedPositions"
            },
        )
    }

    private fun matchExecutions(
        valuesToCheck: List<List<Any?>>,
        predicates: Array<out Function<Boolean>>,
    ) {
        require(valuesToCheck.size == predicates.size) {
            "Expected to find ${predicates.size} executions, but got ${valuesToCheck.size} instead. " +
                    "They must be the same in $MATCH_EXECUTIONS mode."
        }

        check(
            valuesToCheck,
            predicates,
            successCriteria = { array -> array.all { it == 1 } },
            errorMessage = { array ->
                buildString {
                    append("Some predicates where not satisfied or were satisfied more than once:")
                    appendLine()
                    val message = array
                        .withIndex()
                        .filter { it.value != 1 }
                        .map { (index, value) -> "Predicate with index $index satisfied $value times" }
                        .joinToString(System.lineSeparator()) { "\t$it" }
                    append(message)
                }
            },
        )
    }

    private fun check(
        valuesToCheck: List<List<Any?>>,
        predicates: Array<out Function<Boolean>>,
        successCriteria: (IntArray) -> Boolean,
        errorMessage: (IntArray) -> String,
    ) {
        val satisfied = IntArray(predicates.size) { 0 }

        valuesToCheck.forEach { values ->
            predicates.forEachIndexed { index, predicate ->
                val isSatisfied = invokeFunction(predicate, values)
                if (isSatisfied) {
                    satisfied[index]++
                }
            }
        }

        val isSuccess = successCriteria(satisfied)

        check(isSuccess) {
            buildString {
                appendLine(errorMessage(satisfied))
            }
        }
    }

    /**
     * Modes for strategy of checking result matchers.
     *
     * * [MATCH_EXECUTIONS] is used for verification of results in one-to-one way:
     * each matcher should be satisfied with one and only one [AnalysisResult].
     *
     * * [MATCH_PROPERTIES] is about discovered properties.
     * It checks whether each of the properties from the matchers
     * were covered by at least one [AnalysisResult].
     */
    enum class CheckMode {
        MATCH_PROPERTIES, MATCH_EXECUTIONS
    }

    private fun invokeFunction(matcher: Function<Boolean>, params: List<Any?>): Boolean = runCatching {
        matcher.call(params)
    }.getOrDefault(false) // exceptions leads to a failed matcher

    @Suppress("UNCHECKED_CAST")
    // TODO please use matcher.reflect().call(...) when it will be ready,
    //      currently (Kotlin 1.8.22) call isn't fully supported in kotlin reflect
    private fun <T> Function<T>.call(params: List<Any?>): T = when (this) {
        is Function1<*, *> -> (this as Function1<Any?, T>).invoke(params[0])
        is Function2<*, *, *> -> (this as Function2<Any?, Any?, T>).invoke(params[0], params[1])
        is Function3<*, *, *, *> -> (this as Function3<Any?, Any?, Any?, T>).invoke(
            params[0], params[1], params[2]
        )

        is Function4<*, *, *, *, *> -> (this as Function4<Any?, Any?, Any?, Any?, T>).invoke(
            params[0], params[1], params[2], params[3]
        )

        is Function5<*, *, *, *, *, *> -> (this as Function5<Any?, Any?, Any?, Any?, Any?, T>).invoke(
            params[0], params[1], params[2], params[3], params[4],
        )

        is Function6<*, *, *, *, *, *, *> -> (this as Function6<Any?, Any?, Any?, Any?, Any?, Any?, T>).invoke(
            params[0], params[1], params[2], params[3], params[4], params[5],
        )

        is Function7<*, *, *, *, *, *, *, *> -> (this as Function7<Any?, Any?, Any?, Any?, Any?, Any?, Any?, T>).invoke(
            params[0], params[1], params[2], params[3], params[4], params[5], params[6],
        )

        else -> error("Functions with arity > 7 are not supported")
    }
}

fun Any?.safeToString(): String = try {
    toString()
} catch (ex: Throwable) {
    "(ERROR: ${ex.message})"
}
