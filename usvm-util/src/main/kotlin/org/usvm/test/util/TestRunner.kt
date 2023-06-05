package org.usvm.test.util

import org.usvm.test.util.TestRunner.CheckMode.MATCH_EXECUTIONS
import org.usvm.test.util.TestRunner.CheckMode.MATCH_PROPERTIES
import org.usvm.test.util.checkers.AnalysisResultsNumberMatcher
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.ExperimentalReflectionOnLambdas
import kotlin.reflect.jvm.reflect

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
@OptIn(ExperimentalReflectionOnLambdas::class)
open class TestRunner<AnalysisResult, Target, Type, Coverage>(
    val typeTransformer: (Any?) -> Type,
    val runner: (Target) -> List<AnalysisResult>,
    val coverageRunner: (List<AnalysisResult>) -> Coverage,
) {
    /**
     * Runs an interpreter on the [target], after that makes several checks of the results it got:
     * * whether the interpreter produces as many results as we expected using [analysisResultsNumberMatcher];
     * * whether all [analysisResultsMatchers] are satisfied with respect to the [checkMode];
     * * whether all types in the results matches with the expected ones ([expectedTypesForExtractedValues]);
     * * whether we got an expected coverage result ([coverageChecker]).
     *
     * During the process, it extracts values required for
     * checks from the [AnalysisResult] using [extractValuesToCheck].
     */
    protected fun internalCheck(
        target: Target,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher,
        analysisResultsMatchers: Array<out KFunction<Boolean>>,
        extractValuesToCheck: (AnalysisResult) -> List<Any?>,
        expectedTypesForExtractedValues: Array<out Type>,
        checkMode: CheckMode,
        coverageChecker: (Coverage) -> Boolean,
    ) {
        val analysisResults = runner(target)

        require(analysisResultsNumberMatcher(analysisResults.size)) {
            analysisResultsNumberMatcher.matcherFailedMessage(analysisResults.size)
        }

        val valuesToCheck = analysisResults.map { extractValuesToCheck(it) }

        checkTypes(expectedTypesForExtractedValues, valuesToCheck)

        when (checkMode) {
            MATCH_EXECUTIONS -> matchExecutions(valuesToCheck, analysisResultsMatchers)
            MATCH_PROPERTIES -> checkDiscoveredProperties(valuesToCheck, analysisResultsMatchers)
        }

        val coverageResult = coverageRunner(analysisResults)

        require(coverageChecker(coverageResult)) {
            "Coverage check failed: $coverageChecker, result: $coverageResult"
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
                .filter { it.value.first != it.value.second }

            check(mismatchedTypes.isEmpty()) {
                "Some types don't match at positions (from 0): ${mismatchedTypes.map { it.index }}"
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
            }
        )
    }

    private fun matchExecutions(
        valuesToCheck: List<List<Any?>>,
        predicates: Array<out Function<Boolean>>,
    ) {
        check(
            valuesToCheck,
            predicates,
            successCriteria = { array -> valuesToCheck.size == predicates.size && array.all { it == 1 } },
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
            }
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
                val isSatisfied = predicate.reflect()!!.call(values)
                if (isSatisfied) {
                    satisfied[index]++
                }
            }
        }

        val isSuccess = successCriteria(satisfied)

        check(isSuccess) { errorMessage(satisfied) }
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
}