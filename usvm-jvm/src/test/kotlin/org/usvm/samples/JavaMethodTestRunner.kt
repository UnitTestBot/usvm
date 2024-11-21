package org.usvm.samples

import org.jacodb.api.jvm.JcClassOrInterface
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.usvm.CoverageZone
import org.usvm.PathSelectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.JcClassCoverage
import org.usvm.api.JcParametersState
import org.usvm.api.JcTest
import org.usvm.api.StaticFieldValue
import org.usvm.api.targets.JcTarget
import org.usvm.api.util.JcTestInterpreter
import org.usvm.machine.JcInterpreterObserver
import org.usvm.machine.JcMachine
import org.usvm.test.util.TestRunner
import org.usvm.test.util.checkers.AnalysisResultsNumberMatcher
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.JcTestExecutor
import org.usvm.util.JcTestResolverType
import org.usvm.util.UTestRunnerController
import org.usvm.util.getJcMethodByName
import org.usvm.util.loadClasspathFromEnv
import java.io.File
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KFunction1
import kotlin.reflect.KFunction2
import kotlin.reflect.KFunction3
import kotlin.reflect.KFunction4
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.jvm.javaConstructor
import kotlin.reflect.jvm.javaMethod
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds


@ExtendWith(UTestRunnerController::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
open class JavaMethodTestRunner : TestRunner<JcTest, KFunction<*>, KClass<*>?, JcClassCoverage>() {

    private var targets: List<JcTarget> = emptyList()
    private var interpreterObserver: JcInterpreterObserver? = null

    /**
     * Sets JcTargets to run JcMachine with in the scope of [action].
     */
    protected fun <T> withTargets(
        targets: List<JcTarget>,
        interpreterObserver: JcInterpreterObserver,
        action: () -> T,
    ): T {
        val prevTargets = this.targets
        val prevInterpreterObserver = this.interpreterObserver
        try {
            this.targets = targets
            this.interpreterObserver = interpreterObserver
            return action()
        } finally {
            this.targets = prevTargets
            this.interpreterObserver = prevInterpreterObserver
        }
    }

    // region Default checkers

    protected inline fun <reified T, reified R> checkExecutionBranches(
        method: KFunction1<T, R>,
        vararg analysisResultsMatchers: (T, R?) -> Boolean,
        invariants: Array<(T, R?) -> Boolean> = emptyArray(),
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
    ) {
        checkMatches(
            method,
            ignoreNumberOfAnalysisResults,
            *analysisResultsMatchers,
            invariants = invariants,
            coverageChecker = coverageChecker,
            checkMode = CheckMode.MATCH_EXECUTIONS
        )
    }

    protected inline fun <reified T, reified R> checkDiscoveredProperties(
        method: KFunction1<T, R>,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher,
        vararg analysisResultsMatchers: (T, R?) -> Boolean,
        invariants: Array<(T, R?) -> Boolean> = emptyArray(),
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
    ) {
        checkMatches(
            method,
            analysisResultsNumberMatcher,
            analysisResultsMatchers = analysisResultsMatchers,
            invariants = invariants,
            coverageChecker = coverageChecker,
            checkMode = CheckMode.MATCH_PROPERTIES
        )
    }

    protected inline fun <reified T, reified R> checkDiscoveredPropertiesWithStatics(
        method: KFunction1<T, R>,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher,
        vararg analysisResultsMatchers: (T, R?, StaticsType, StaticsType) -> Boolean,
        invariants: Array<(T, R?) -> Boolean> = emptyArray(),
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
        checkMode: CheckMode,
    ) {
        internalCheck(
            target = method,
            analysisResultsNumberMatcher,
            analysisResultsMatchers,
            invariants = invariants,
            extractValuesToCheck = { test: JcTest -> test.takeParametersBeforeAndAllStaticsWithResult(method) },
            expectedTypesForExtractedValues = arrayOf(T::class, R::class, Map::class, Map::class),
            checkMode = checkMode,
            coverageChecker
        )
    }

    protected inline fun <reified T, reified R> checkMatches(
        method: KFunction1<T, R>,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher,
        vararg analysisResultsMatchers: (T, R?) -> Boolean,
        invariants: Array<(T, R?) -> Boolean> = emptyArray(),
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
        checkMode: CheckMode,
    ) {
        internalCheck(
            target = method,
            analysisResultsNumberMatcher,
            analysisResultsMatchers,
            invariants = invariants,
            extractValuesToCheck = { test: JcTest -> test.takeAllParametersBeforeWithResult(method) },
            expectedTypesForExtractedValues = arrayOf(T::class, R::class),
            checkMode = checkMode,
            coverageChecker
        )
    }

    protected inline fun <reified T, reified R> checkThisAndParamsMutations(
        method: KFunction1<T, R>,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher,
        vararg paramsMutationsMatchers: (T, T, R?) -> Boolean,
        invariants: Array<(T, T, R?) -> Boolean> = emptyArray(),
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
        checkMode: CheckMode,
    ) {
        internalCheck(
            target = method,
            analysisResultsNumberMatcher,
            paramsMutationsMatchers,
            invariants = invariants,
            extractValuesToCheck = { test: JcTest -> test.takeAllParametersBeforeAndAfterWithResult(method) },
            expectedTypesForExtractedValues = arrayOf(T::class, T::class, R::class),
            checkMode = checkMode,
            coverageChecker
        )
    }

    protected inline fun <reified T, reified A0, reified R> checkExecutionBranches(
        method: KFunction2<T, A0, R>,
        vararg analysisResultsMatchers: (T, A0, R?) -> Boolean,
        invariants: Array<(T, A0, R?) -> Boolean> = emptyArray(),
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
    ) {
        checkMatches(
            method,
            ignoreNumberOfAnalysisResults,
            *analysisResultsMatchers,
            invariants = invariants,
            coverageChecker = coverageChecker,
            checkMode = CheckMode.MATCH_EXECUTIONS
        )
    }

    protected inline fun <reified T, reified A0, reified R> checkDiscoveredProperties(
        method: KFunction2<T, A0, R>,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher,
        vararg analysisResultsMatchers: (T, A0, R?) -> Boolean,
        invariants: Array<(T, A0, R?) -> Boolean> = emptyArray(),
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
    ) {
        checkMatches(
            method,
            analysisResultsNumberMatcher,
            analysisResultsMatchers = analysisResultsMatchers,
            invariants = invariants,
            coverageChecker = coverageChecker,
            checkMode = CheckMode.MATCH_PROPERTIES
        )
    }

    protected inline fun <reified T, reified A0, reified R> checkMatches(
        method: KFunction2<T, A0, R>,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher,
        vararg analysisResultsMatchers: (T, A0, R?) -> Boolean,
        invariants: Array<(T, A0, R?) -> Boolean> = emptyArray(),
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
        checkMode: CheckMode,
    ) {
        internalCheck(
            target = method,
            analysisResultsNumberMatcher,
            analysisResultsMatchers,
            invariants = invariants,
            extractValuesToCheck = { test: JcTest -> test.takeAllParametersBeforeWithResult(method) },
            expectedTypesForExtractedValues = arrayOf(T::class, A0::class, R::class),
            checkMode = checkMode,
            coverageChecker
        )
    }

    protected inline fun <reified T, reified A0, reified R> checkThisAndParamsMutations(
        method: KFunction2<T, A0, R>,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher,
        vararg paramsMutationsMatchers: (T, A0, T, A0, R?) -> Boolean,
        invariants: Array<(T, A0, T, A0, R?) -> Boolean> = emptyArray(),
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
        checkMode: CheckMode,
    ) {
        internalCheck(
            target = method,
            analysisResultsNumberMatcher,
            paramsMutationsMatchers,
            invariants = invariants,
            extractValuesToCheck = { test: JcTest -> test.takeAllParametersBeforeAndAfterWithResult(method) },
            expectedTypesForExtractedValues = arrayOf(T::class, A0::class, T::class, A0::class, R::class),
            checkMode = checkMode,
            coverageChecker
        )
    }

    protected inline fun <reified T, reified A0, reified A1, reified R> checkExecutionBranches(
        method: KFunction3<T, A0, A1, R>,
        vararg analysisResultsMatchers: (T, A0, A1, R?) -> Boolean,
        invariants: Array<(T, A0, A1, R?) -> Boolean> = emptyArray(),
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
    ) {
        checkMatches(
            method,
            ignoreNumberOfAnalysisResults,
            *analysisResultsMatchers,
            invariants = invariants,
            coverageChecker = coverageChecker,
            checkMode = CheckMode.MATCH_EXECUTIONS
        )
    }

    protected inline fun <reified T, reified A0, reified A1, reified R> checkDiscoveredProperties(
        method: KFunction3<T, A0, A1, R>,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher,
        vararg analysisResultsMatchers: (T, A0, A1, R?) -> Boolean,
        invariants: Array<(T, A0, A1, R?) -> Boolean> = emptyArray(),
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
    ) {
        checkMatches(
            method,
            analysisResultsNumberMatcher,
            analysisResultsMatchers = analysisResultsMatchers,
            invariants = invariants,
            coverageChecker = coverageChecker,
            checkMode = CheckMode.MATCH_PROPERTIES
        )
    }

    protected inline fun <reified T, reified A0, reified A1, reified R> checkMatches(
        method: KFunction3<T, A0, A1, R>,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher,
        vararg analysisResultsMatchers: (T, A0, A1, R?) -> Boolean,
        invariants: Array<(T, A0, A1, R?) -> Boolean> = emptyArray(),
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
        checkMode: CheckMode,
    ) {
        internalCheck(
            target = method,
            analysisResultsNumberMatcher,
            analysisResultsMatchers,
            invariants = invariants,
            extractValuesToCheck = { test: JcTest -> test.takeAllParametersBeforeWithResult(method) },
            expectedTypesForExtractedValues = arrayOf(T::class, A0::class, A1::class, R::class),
            checkMode = checkMode,
            coverageChecker
        )
    }

    protected inline fun <reified T, reified A0, reified A1, reified R> checkThisAndParamsMutations(
        method: KFunction3<T, A0, A1, R>,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher,
        vararg paramsMutationsMatchers: (T, A0, A1, T, A0, A1, R?) -> Boolean,
        invariants: Array<(T, A0, A1, T, A0, A1, R?) -> Boolean> = emptyArray(),
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
        checkMode: CheckMode,
    ) {
        internalCheck(
            target = method,
            analysisResultsNumberMatcher,
            paramsMutationsMatchers,
            invariants = invariants,
            extractValuesToCheck = { test: JcTest -> test.takeAllParametersBeforeAndAfterWithResult(method) },
            expectedTypesForExtractedValues = arrayOf(T::class, A0::class, A1::class, T::class, A0::class, A1::class, R::class),
            checkMode = checkMode,
            coverageChecker
        )
    }

    protected inline fun <reified T, reified A0, reified A1, reified A2, reified R> checkExecutionBranches(
        method: KFunction4<T, A0, A1, A2, R>,
        vararg analysisResultsMatchers: (T, A0, A1, A2, R?) -> Boolean,
        invariants: Array<(T, A0, A1, A2, R?) -> Boolean> = emptyArray(),
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
    ) {
        checkMatches(
            method,
            ignoreNumberOfAnalysisResults,
            *analysisResultsMatchers,
            invariants = invariants,
            coverageChecker = coverageChecker,
            checkMode = CheckMode.MATCH_EXECUTIONS
        )
    }

    protected inline fun <reified T, reified A0, reified A1, reified A2, reified R> checkDiscoveredProperties(
        method: KFunction4<T, A0, A1, A2, R>,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher,
        vararg analysisResultsMatchers: (T, A0, A1, A2, R?) -> Boolean,
        invariants: Array<(T, A0, A1, A2, R?) -> Boolean> = emptyArray(),
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
    ) {
        checkMatches(
            method,
            analysisResultsNumberMatcher,
            analysisResultsMatchers = analysisResultsMatchers,
            invariants = invariants,
            coverageChecker = coverageChecker,
            checkMode = CheckMode.MATCH_PROPERTIES
        )
    }

    protected inline fun <reified T, reified A0, reified A1, reified A2, reified R> checkMatches(
        method: KFunction4<T, A0, A1, A2, R>,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher,
        vararg analysisResultsMatchers: (T, A0, A1, A2, R?) -> Boolean,
        invariants: Array<out Function<Boolean>> = emptyArray(),
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
        checkMode: CheckMode,
    ) {
        internalCheck(
            target = method,
            analysisResultsNumberMatcher,
            analysisResultsMatchers,
            invariants = invariants,
            extractValuesToCheck = { test: JcTest -> test.takeAllParametersBeforeWithResult(method) },
            expectedTypesForExtractedValues = arrayOf(T::class, A0::class, A1::class, A2::class, R::class),
            checkMode = checkMode,
            coverageChecker
        )
    }

    protected inline fun <reified T, reified A0, reified A1, reified A2, reified R> checkThisAndParamsMutations(
        method: KFunction4<T, A0, A1, A2, R>,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher,
        vararg paramsMutationsMatchers: (T, A0, A1, A2, T, A0, A1, A2, R?) -> Boolean,
        invariants: Array<(T, A0, A1, A2, T, A0, A1, A2, R?) -> Boolean> = emptyArray(),
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
        checkMode: CheckMode,
    ) {
        internalCheck(
            target = method,
            analysisResultsNumberMatcher,
            paramsMutationsMatchers,
            invariants = invariants,
            extractValuesToCheck = { test: JcTest -> test.takeAllParametersBeforeAndAfterWithResult(method) },
            expectedTypesForExtractedValues = arrayOf(
                T::class,
                A0::class,
                A1::class,
                A2::class,
                T::class,
                A0::class,
                A1::class,
                A2::class,
                R::class
            ),
            checkMode = checkMode,
            coverageChecker
        )
    }

    // endregion

    // region Default checkers with exceptions

    protected inline fun <reified T, reified R> checkExecutionBranchesWithExceptions(
        method: KFunction1<T, R>,
        vararg analysisResultsMatchers: (T, Result<R>) -> Boolean,
        invariants: Array<(T, Result<R>) -> Boolean> = emptyArray(),
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
    ) {
        checkMatchesWithExceptions(
            method,
            ignoreNumberOfAnalysisResults,
            *analysisResultsMatchers,
            invariants = invariants,
            coverageChecker = coverageChecker,
            checkMode = CheckMode.MATCH_EXECUTIONS
        )
    }

    protected inline fun <reified T, reified R> checkDiscoveredPropertiesWithExceptions(
        method: KFunction1<T, R>,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher,
        vararg analysisResultsMatchers: (T, Result<R>) -> Boolean,
        invariants: Array<(T, Result<R>) -> Boolean> = emptyArray(),
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
    ) {
        checkMatchesWithExceptions(
            method,
            analysisResultsNumberMatcher,
            analysisResultsMatchers = analysisResultsMatchers,
            invariants = invariants,
            coverageChecker = coverageChecker,
            checkMode = CheckMode.MATCH_PROPERTIES
        )
    }

    protected inline fun <reified T, reified R> checkMatchesWithExceptions(
        method: KFunction1<T, R>,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher,
        vararg analysisResultsMatchers: (T, Result<R>) -> Boolean,
        invariants: Array<(T, Result<R>) -> Boolean> = emptyArray(),
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
        checkMode: CheckMode,
    ) {
        internalCheck(
            target = method,
            analysisResultsNumberMatcher,
            analysisResultsMatchers,
            invariants = invariants,
            extractValuesToCheck = { test: JcTest ->
                val values = test.takeAllParametersBefore(method)
                test.result.let { values += it }
                values
            },
            expectedTypesForExtractedValues = arrayOf(T::class), // We don't check type for the result here
            checkMode = checkMode,
            coverageChecker
        )
    }

    protected inline fun <reified T, reified R> checkThisAndParamsMutationsWithExceptions(
        method: KFunction1<T, R>,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher,
        vararg paramsMutationsMatchers: (T, T, Result<R>) -> Boolean,
        invariants: Array<(T, T, Result<R>) -> Boolean> = emptyArray(),
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
        checkMode: CheckMode,
    ) {
        internalCheck(
            target = method,
            analysisResultsNumberMatcher,
            paramsMutationsMatchers,
            invariants = invariants,
            extractValuesToCheck = { test: JcTest -> test.takeAllParametersBeforeAndAfterWithResult(method) },
            // We don't check type for the result here
            expectedTypesForExtractedValues = arrayOf(T::class, T::class),
            checkMode = checkMode,
            coverageChecker
        )
    }

    protected inline fun <reified T, reified A0, reified R> checkExecutionBranchesWithExceptions(
        method: KFunction2<T, A0, R>,
        vararg analysisResultsMatchers: (T, A0, Result<R>) -> Boolean,
        invariants: Array<(T, A0, Result<R>) -> Boolean> = emptyArray(),
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
    ) {
        checkMatchesWithExceptions(
            method,
            ignoreNumberOfAnalysisResults,
            *analysisResultsMatchers,
            invariants = invariants,
            coverageChecker = coverageChecker,
            checkMode = CheckMode.MATCH_EXECUTIONS
        )
    }

    protected inline fun <reified T, reified A0, reified R> checkDiscoveredPropertiesWithExceptions(
        method: KFunction2<T, A0, R>,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher,
        vararg analysisResultsMatchers: (T, A0, Result<R>) -> Boolean,
        invariants: Array<(T, A0, Result<R>) -> Boolean> = emptyArray(),
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
    ) {
        checkMatchesWithExceptions(
            method,
            analysisResultsNumberMatcher,
            analysisResultsMatchers = analysisResultsMatchers,
            invariants = invariants,
            coverageChecker = coverageChecker,
            checkMode = CheckMode.MATCH_PROPERTIES
        )
    }

    protected inline fun <reified T, reified A0, reified R> checkMatchesWithExceptions(
        method: KFunction2<T, A0, R>,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher,
        vararg analysisResultsMatchers: (T, A0, Result<R>) -> Boolean,
        invariants: Array<(T, A0, Result<R>) -> Boolean> = emptyArray(),
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
        checkMode: CheckMode,
    ) {
        internalCheck(
            target = method,
            analysisResultsNumberMatcher,
            analysisResultsMatchers,
            invariants = invariants,
            extractValuesToCheck = { test: JcTest ->
                val values = test.takeAllParametersBefore(method)
                test.result.let { values += it }
                values
            },
            expectedTypesForExtractedValues = arrayOf(T::class, A0::class), // We don't check type for the result here
            checkMode = checkMode,
            coverageChecker
        )
    }

    protected inline fun <reified T, reified A0, reified R> checkThisAndParamsMutationsWithExceptions(
        method: KFunction2<T, A0, R>,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher,
        vararg paramsMutationsMatchers: (T, A0, T, A0, Result<R>) -> Boolean,
        invariants: Array<(T, A0, T, A0, Result<R>) -> Boolean> = emptyArray(),
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
        checkMode: CheckMode,
    ) {
        internalCheck(
            target = method,
            analysisResultsNumberMatcher,
            paramsMutationsMatchers,
            invariants = invariants,
            extractValuesToCheck = { test: JcTest -> test.takeAllParametersBeforeAndAfterWithResult(method) },
            // We don't check type for the result here
            expectedTypesForExtractedValues = arrayOf(T::class, A0::class, T::class, A0::class),
            checkMode = checkMode,
            coverageChecker
        )
    }

    protected inline fun <reified T, reified A0, reified A1, reified R> checkExecutionBranchesWithExceptions(
        method: KFunction3<T, A0, A1, R>,
        vararg analysisResultsMatchers: (T, A0, A1, Result<R>) -> Boolean,
        invariants: Array<(T, A0, A1, Result<R>) -> Boolean> = emptyArray(),
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
    ) {
        checkMatchesWithExceptions(
            method,
            ignoreNumberOfAnalysisResults,
            *analysisResultsMatchers,
            invariants = invariants,
            coverageChecker = coverageChecker,
            checkMode = CheckMode.MATCH_EXECUTIONS
        )
    }

    protected inline fun <reified T, reified A0, reified A1, reified R> checkDiscoveredPropertiesWithExceptions(
        method: KFunction3<T, A0, A1, R>,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher,
        vararg analysisResultsMatchers: (T, A0, A1, Result<R>) -> Boolean,
        invariants: Array<(T, A0, A1, Result<R>) -> Boolean> = emptyArray(),
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
    ) {
        checkMatchesWithExceptions(
            method,
            analysisResultsNumberMatcher,
            analysisResultsMatchers = analysisResultsMatchers,
            invariants = invariants,
            coverageChecker = coverageChecker,
            checkMode = CheckMode.MATCH_PROPERTIES
        )
    }

    protected inline fun <reified T, reified A0, reified A1, reified R> checkMatchesWithExceptions(
        method: KFunction3<T, A0, A1, R>,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher,
        vararg analysisResultsMatchers: (T, A0, A1, Result<R>) -> Boolean,
        invariants: Array<(T, A0, A1, Result<R>) -> Boolean> = emptyArray(),
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
        checkMode: CheckMode,
    ) {
        internalCheck(
            target = method,
            analysisResultsNumberMatcher,
            analysisResultsMatchers,
            invariants = invariants,
            extractValuesToCheck = { test: JcTest ->
                val values = test.takeAllParametersBefore(method)
                test.result.let { values += it }
                values
            },
            // We don't check type for the result here
            expectedTypesForExtractedValues = arrayOf(T::class, A0::class, A1::class),
            checkMode = checkMode,
            coverageChecker
        )
    }

    protected inline fun <reified T, reified A0, reified A1, reified R> checkThisAndParamsMutationsWithExceptions(
        method: KFunction3<T, A0, A1, R>,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher,
        vararg paramsMutationsMatchers: (T, A0, A1, T, A0, A1, Result<R>) -> Boolean,
        invariants: Array<(T, A0, A1, T, A0, A1, Result<R>) -> Boolean> = emptyArray(),
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
        checkMode: CheckMode,
    ) {
        internalCheck(
            target = method,
            analysisResultsNumberMatcher,
            paramsMutationsMatchers,
            invariants = invariants,
            extractValuesToCheck = { test: JcTest -> test.takeAllParametersBeforeAndAfterWithResult(method) },
            // We don't check type for the result here
            expectedTypesForExtractedValues = arrayOf(T::class, A0::class, A1::class, T::class, A0::class, A1::class),
            checkMode = checkMode,
            coverageChecker
        )
    }

    protected inline fun <reified T, reified A0, reified A1, reified A2, reified R> checkExecutionBranchesWithExceptions(
        method: KFunction4<T, A0, A1, A2, R>,
        vararg analysisResultsMatchers: (T, A0, A1, A2, Result<R>) -> Boolean,
        invariants: Array<(T, A0, A1, A2, Result<R>) -> Boolean> = emptyArray(),
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
    ) {
        checkMatchesWithExceptions(
            method,
            ignoreNumberOfAnalysisResults,
            *analysisResultsMatchers,
            invariants = invariants,
            coverageChecker = coverageChecker,
            checkMode = CheckMode.MATCH_EXECUTIONS
        )
    }

    protected inline fun <reified T, reified A0, reified A1, reified A2, reified R> checkDiscoveredPropertiesWithExceptions(
        method: KFunction4<T, A0, A1, A2, R>,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher,
        vararg analysisResultsMatchers: (T, A0, A1, A2, Result<R>) -> Boolean,
        invariants: Array<(T, A0, A1, A2, Result<R>) -> Boolean> = emptyArray(),
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
    ) {
        checkMatchesWithExceptions(
            method,
            analysisResultsNumberMatcher,
            analysisResultsMatchers = analysisResultsMatchers,
            invariants = invariants,
            coverageChecker = coverageChecker,
            checkMode = CheckMode.MATCH_PROPERTIES
        )
    }

    protected inline fun <reified T, reified A0, reified A1, reified A2, reified R> checkMatchesWithExceptions(
        method: KFunction4<T, A0, A1, A2, R>,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher,
        vararg analysisResultsMatchers: (T, A0, A1, A2, Result<R>) -> Boolean,
        invariants: Array<(T, A0, A1, A2, Result<R>) -> Boolean> = emptyArray(),
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
        checkMode: CheckMode,
    ) {
        internalCheck(
            target = method,
            analysisResultsNumberMatcher,
            analysisResultsMatchers,
            invariants = invariants,
            extractValuesToCheck = { test: JcTest ->
                val values = test.takeAllParametersBefore(method)
                test.result.let { values += it }
                values
            },
            // We don't check type for the result here
            expectedTypesForExtractedValues = arrayOf(T::class, A0::class, A1::class, A2::class),
            checkMode = checkMode,
            coverageChecker
        )
    }

    protected inline fun <reified T, reified A0, reified A1, reified A2, reified R> checkThisAndParamsMutationsWithExceptions(
        method: KFunction4<T, A0, A1, A2, R>,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher,
        vararg paramsMutationsMatchers: (T, A0, A1, A2, T, A0, A1, A2, Result<R>) -> Boolean,
        invariants: Array<(T, A0, A1, A2, T, A0, A1, A2, Result<R>) -> Boolean> = emptyArray(),
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
        checkMode: CheckMode,
    ) {
        internalCheck(
            target = method,
            analysisResultsNumberMatcher,
            paramsMutationsMatchers,
            invariants = invariants,
            extractValuesToCheck = { test: JcTest -> test.takeAllParametersBeforeAndAfterWithResult(method) },
            // We don't check type for the result here
            expectedTypesForExtractedValues = arrayOf(
                T::class,
                A0::class,
                A1::class,
                A2::class,
                T::class,
                A0::class,
                A1::class,
                A2::class
            ),
            checkMode = checkMode,
            coverageChecker
        )
    }

    // endregion

    protected fun JcTest.takeAllParametersBefore(method: KFunction<*>): MutableList<Any?> =
        before.takeAllParameters(method)

    protected fun JcTest.takeAllParametersBeforeWithResult(method: KFunction<*>): MutableList<Any?> {
        val values = before.takeAllParameters(method)
        result.let { values += it.getOrNull() }

        return values
    }

    protected fun JcTest.takeAllParametersAfter(method: KFunction<*>): MutableList<Any?> =
        after.takeAllParameters(method)

    protected fun JcTest.takeAllParametersAfterWithResult(method: KFunction<*>): MutableList<Any?> {
        val values = after.takeAllParameters(method)
        result.let { values += it.getOrNull() }

        return values
    }

    private fun JcTest.takeAllParametersBeforeAndAfter(method: KFunction<*>): MutableList<Any?> {
        val parameters = before.takeAllParameters(method)
        parameters.addAll(after.takeAllParameters(method))

        return parameters
    }

    protected fun JcTest.takeAllParametersBeforeAndAfterWithResult(method: KFunction<*>): MutableList<Any?> {
        val values = takeAllParametersBeforeAndAfter(method)
        values += result.getOrNull()


        return values
    }

    private fun JcTest.takeStaticsBefore(): Map<JcClassOrInterface, List<StaticFieldValue>> = before.statics
    private fun JcTest.takeStaticsAfter(): Map<JcClassOrInterface, List<StaticFieldValue>> = after.statics

    protected fun JcTest.takeParametersBeforeAndAllStaticsWithResult(method: KFunction<*>): MutableList<Any?> {
        val values = takeAllParametersBefore(method)

        values += result.getOrNull()
        values += takeStaticsBefore()
        values += takeStaticsAfter()

        return values
    }

    private fun JcParametersState.takeAllParameters(
        method: KFunction<*>,
    ): MutableList<Any?> {
        val values = mutableListOf<Any?>()
        if (method.instanceParameter != null && method.javaConstructor == null) {
            values += requireNotNull(thisInstance)
        } else {
            // Note that for constructors we have thisInstance in such as case, in contrast to simple methods
            require(thisInstance == null || method.javaConstructor != null)
        }

        values.addAll(parameters) // add remaining arguments
        return values
    }

    protected open val jacodbCpKey: String
        get() = samplesKey

    protected open val classpath: List<File>
        get() = samplesClasspath

    protected val cp by lazy {
        JacoDBContainer(jacodbCpKey, classpath).cp
    }

    protected open val resolverType: JcTestResolverType = JcTestResolverType.INTERPRETER

    private val testResolver =
        when (resolverType) {
            JcTestResolverType.INTERPRETER -> JcTestInterpreter()
            JcTestResolverType.CONCRETE_EXECUTOR -> JcTestExecutor(classpath = cp)
        }

    override val typeTransformer: (Any?) -> KClass<*>? = { value -> value?.let { it::class } }

    override val checkType: (KClass<*>?, KClass<*>?) -> Boolean =
        { expected, actual -> actual == null || expected != null && expected.java.isAssignableFrom(actual.java) }

    override var options: UMachineOptions = UMachineOptions(
        pathSelectionStrategies = listOf(PathSelectionStrategy.FORK_DEPTH),
        coverageZone = CoverageZone.TRANSITIVE,
        exceptionsPropagation = true,
        timeout = 60_000.milliseconds,
        stepsFromLastCovered = 3500L,
        solverTimeout = Duration.INFINITE, // we do not need the timeout for a solver in tests
        typeOperationsTimeout = Duration.INFINITE, // we do not need the timeout for type operations in tests
    )

    override val runner: (KFunction<*>, UMachineOptions) -> List<JcTest> = { method, options ->
        val jcMethod = cp.getJcMethodByName(method)

        JcMachine(cp, options, interpreterObserver = interpreterObserver).use { machine ->
            val states = machine.analyze(jcMethod.method, targets)
            states.map { testResolver.resolve(jcMethod, it) }
        }
    }

    override val coverageRunner: (List<JcTest>) -> JcClassCoverage = { _ ->
        JcClassCoverage(visitedStmts = emptySet())
    }

    companion object {
        private const val ENV_TEST_SAMPLES = "usvm.jvm.test.samples"

        val samplesClasspath by lazy {
            loadClasspathFromEnv(ENV_TEST_SAMPLES)
        }

        init {
            // See https://dzone.com/articles/how-to-export-all-modules-to-all-modules-at-runtime-in-java?preview=true
            org.burningwave.core.assembler.StaticComponentContainer.Modules.exportAllToAll()
        }
    }
}

private val KFunction<*>.declaringClass: Class<*>?
    get() = (javaMethod ?: javaConstructor)?.declaringClass

private typealias StaticsType = Map<JcClassOrInterface, List<StaticFieldValue>>
