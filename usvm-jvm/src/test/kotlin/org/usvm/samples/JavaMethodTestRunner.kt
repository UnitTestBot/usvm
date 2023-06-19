package org.usvm.samples

import org.jacodb.api.ext.findClass
import org.jacodb.api.ext.toType
import org.junit.jupiter.api.TestInstance
import org.usvm.api.JcClassCoverage
import org.usvm.api.JcTest
import org.usvm.api.util.JcTestResolver
import org.usvm.machine.JcMachine
import org.usvm.test.util.TestRunner
import org.usvm.test.util.checkers.AnalysisResultsNumberMatcher
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KFunction1
import kotlin.reflect.KFunction2
import kotlin.reflect.KFunction3
import kotlin.reflect.KFunction4
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.javaMethod


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
open class JavaMethodTestRunner : TestRunner<JcTest, KFunction<*>, KClass<*>?, JcClassCoverage>() {
    // region Default checkers

    protected inline fun <reified T, reified R> checkPropertiesMatches(
        method: KFunction1<T, R>,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher,
        vararg analysisResultsMatchers: (T, R) -> Boolean,
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
    ) {
        checkMatches(
            method,
            analysisResultsNumberMatcher,
            *analysisResultsMatchers,
            coverageChecker = coverageChecker,
            checkMode = CheckMode.MATCH_PROPERTIES
        )
    }

    protected inline fun <reified T, reified R> checkExecutionMatches(
        method: KFunction1<T, R>,
        vararg analysisResultsMatchers: (T, R) -> Boolean,
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
    ) {
        checkMatches(
            method,
            analysisResultsMatchers = analysisResultsMatchers,
            coverageChecker = coverageChecker,
            checkMode = CheckMode.MATCH_EXECUTIONS
        )
    }

    protected inline fun <reified T, reified R> checkMatches(
        method: KFunction1<T, R>,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
        vararg analysisResultsMatchers: (T, R) -> Boolean,
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
        checkMode: CheckMode,
    ) {
        internalCheck(
            target = method,
            analysisResultsNumberMatcher,
            analysisResultsMatchers,
            extractValuesToCheck = { test: JcTest ->
                val values = test.takeAllParameters(method, allParametersCount = 1)
                test.result.let { values += it.getOrNull() }
                values
            },
            expectedTypesForExtractedValues = arrayOf(T::class, R::class),
            checkMode = checkMode,
            coverageChecker
        )
    }

    protected inline fun <reified T, reified A0, reified R> checkPropertiesMatches(
        method: KFunction2<T, A0, R>,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher,
        vararg analysisResultsMatchers: (T, A0, R) -> Boolean,
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
    ) {
        checkMatches(
            method,
            analysisResultsNumberMatcher,
            *analysisResultsMatchers,
            coverageChecker = coverageChecker,
            checkMode = CheckMode.MATCH_PROPERTIES
        )
    }

    protected inline fun <reified T, reified A0, reified R> checkExecutionMatches(
        method: KFunction2<T, A0, R>,
        vararg analysisResultsMatchers: (T, A0, R) -> Boolean,
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
    ) {
        checkMatches(
            method,
            analysisResultsMatchers = analysisResultsMatchers,
            coverageChecker = coverageChecker,
            checkMode = CheckMode.MATCH_EXECUTIONS
        )
    }

    protected inline fun <reified T, reified A0, reified R> checkMatches(
        method: KFunction2<T, A0, R>,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
        vararg analysisResultsMatchers: (T, A0, R) -> Boolean,
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
        checkMode: CheckMode,
    ) {
        internalCheck(
            target = method,
            analysisResultsNumberMatcher,
            analysisResultsMatchers,
            extractValuesToCheck = { test: JcTest ->
                val values = test.takeAllParameters(method, allParametersCount = 2)
                test.result.let { values += it.getOrNull() }
                values
            },
            expectedTypesForExtractedValues = arrayOf(T::class, A0::class, R::class),
            checkMode = checkMode,
            coverageChecker
        )
    }

    protected inline fun <reified T, reified A0, reified A1, reified R> checkPropertiesMatches(
        method: KFunction3<T, A0, A1, R>,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher,
        vararg analysisResultsMatchers: (T, A0, A1, R) -> Boolean,
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
    ) {
        checkMatches(
            method,
            analysisResultsNumberMatcher,
            *analysisResultsMatchers,
            coverageChecker = coverageChecker,
            checkMode = CheckMode.MATCH_PROPERTIES
        )
    }

    protected inline fun <reified T, reified A0, reified A1, reified R> checkExecutionMatches(
        method: KFunction3<T, A0, A1, R>,
        vararg analysisResultsMatchers: (T, A0, A1, R) -> Boolean,
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
    ) {
        checkMatches(
            method,
            analysisResultsMatchers = analysisResultsMatchers,
            coverageChecker = coverageChecker,
            checkMode = CheckMode.MATCH_EXECUTIONS
        )
    }

    protected inline fun <reified T, reified A0, reified A1, reified R> checkMatches(
        method: KFunction3<T, A0, A1, R>,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
        vararg analysisResultsMatchers: (T, A0, A1, R) -> Boolean,
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
        checkMode: CheckMode,
    ) {
        internalCheck(
            target = method,
            analysisResultsNumberMatcher,
            analysisResultsMatchers,
            extractValuesToCheck = { test: JcTest ->
                val values = test.takeAllParameters(method, allParametersCount = 3)
                test.result.let { values += it.getOrNull() }
                values
            },
            expectedTypesForExtractedValues = arrayOf(T::class, A0::class, A1::class, R::class),
            checkMode = checkMode,
            coverageChecker
        )
    }

    protected inline fun <reified T, reified A0, reified A1, reified A2, reified R> checkPropertiesMatches(
        method: KFunction4<T, A0, A1, A2, R>,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher,
        vararg analysisResultsMatchers: (T, A0, A1, A2, R) -> Boolean,
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
    ) {
        checkMatches(
            method,
            analysisResultsNumberMatcher,
            *analysisResultsMatchers,
            coverageChecker = coverageChecker,
            checkMode = CheckMode.MATCH_PROPERTIES
        )
    }

    protected inline fun <reified T, reified A0, reified A1, reified A2, reified R> checkExecutionMatches(
        method: KFunction4<T, A0, A1, A2, R>,
        vararg analysisResultsMatchers: (T, A0, A1, A2, R) -> Boolean,
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
    ) {
        checkMatches(
            method,
            analysisResultsMatchers = analysisResultsMatchers,
            coverageChecker = coverageChecker,
            checkMode = CheckMode.MATCH_EXECUTIONS
        )
    }

    protected inline fun <reified T, reified A0, reified A1, reified A2, reified R> checkMatches(
        method: KFunction4<T, A0, A1, A2, R>,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
        vararg analysisResultsMatchers: (T, A0, A1, A2, R) -> Boolean,
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
        checkMode: CheckMode,
    ) {
        internalCheck(
            target = method,
            analysisResultsNumberMatcher,
            analysisResultsMatchers,
            extractValuesToCheck = { test: JcTest ->
                val values = test.takeAllParameters(method, allParametersCount = 4)
                test.result.let { values += it.getOrNull() }
                values
            },
            expectedTypesForExtractedValues = arrayOf(T::class, A0::class, A1::class, A2::class, R::class),
            checkMode = checkMode,
            coverageChecker
        )
    }

    // endregion

    // region Default checkers with exceptions

    protected inline fun <reified T, reified R> checkWithExceptionPropertiesMatches(
        method: KFunction1<T, R>,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher,
        vararg analysisResultsMatchers: (T, Result<R>) -> Boolean,
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
    ) {
        checkWithExceptionMatches(
            method,
            analysisResultsNumberMatcher,
            *analysisResultsMatchers,
            coverageChecker = coverageChecker,
            checkMode = CheckMode.MATCH_PROPERTIES
        )
    }

    protected inline fun <reified T, reified R> checkWithExceptionExecutionMatches(
        method: KFunction1<T, R>,
        vararg analysisResultsMatchers: (T, Result<R>) -> Boolean,
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
    ) {
        checkWithExceptionMatches(
            method,
            analysisResultsMatchers = analysisResultsMatchers,
            coverageChecker = coverageChecker,
            checkMode = CheckMode.MATCH_EXECUTIONS
        )
    }

    protected inline fun <reified T, reified R> checkWithExceptionMatches(
        method: KFunction1<T, R>,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
        vararg analysisResultsMatchers: (T, Result<R>) -> Boolean,
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
        checkMode: CheckMode,
    ) {
        internalCheck(
            target = method,
            analysisResultsNumberMatcher,
            analysisResultsMatchers,
            extractValuesToCheck = { test: JcTest ->
                val values = test.takeAllParameters(method, allParametersCount = 1)
                test.result.let { values += it }
                values
            },
            expectedTypesForExtractedValues = arrayOf(T::class), // We don't check type for the result here
            checkMode = checkMode,
            coverageChecker
        )
    }

    protected inline fun <reified T, reified A0, reified R> checkWithExceptionPropertiesMatches(
        method: KFunction2<T, A0, R>,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher,
        vararg analysisResultsMatchers: (T, A0, Result<R>) -> Boolean,
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
    ) {
        checkWithExceptionMatches(
            method,
            analysisResultsNumberMatcher,
            *analysisResultsMatchers,
            coverageChecker = coverageChecker,
            checkMode = CheckMode.MATCH_PROPERTIES
        )
    }

    protected inline fun <reified T, reified A0, reified R> checkWithExceptionExecutionMatches(
        method: KFunction2<T, A0, R>,
        vararg analysisResultsMatchers: (T, A0, Result<R>) -> Boolean,
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
    ) {
        checkWithExceptionMatches(
            method,
            analysisResultsMatchers = analysisResultsMatchers,
            coverageChecker = coverageChecker,
            checkMode = CheckMode.MATCH_EXECUTIONS
        )
    }

    protected inline fun <reified T, reified A0, reified R> checkWithExceptionMatches(
        method: KFunction2<T, A0, R>,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
        vararg analysisResultsMatchers: (T, A0, Result<R>) -> Boolean,
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
        checkMode: CheckMode,
    ) {
        internalCheck(
            target = method,
            analysisResultsNumberMatcher,
            analysisResultsMatchers,
            extractValuesToCheck = { test: JcTest ->
                val values = test.takeAllParameters(method, allParametersCount = 2)
                test.result.let { values += it }
                values
            },
            expectedTypesForExtractedValues = arrayOf(T::class, A0::class), // We don't check type for the result here
            checkMode = checkMode,
            coverageChecker
        )
    }

    protected inline fun <reified T, reified A0, reified A1, reified R> checkWithExceptionPropertiesMatches(
        method: KFunction3<T, A0, A1, R>,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher,
        vararg analysisResultsMatchers: (T, A0, A1, Result<R>) -> Boolean,
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
    ) {
        checkWithExceptionMatches(
            method,
            analysisResultsNumberMatcher,
            *analysisResultsMatchers,
            coverageChecker = coverageChecker,
            checkMode = CheckMode.MATCH_PROPERTIES
        )
    }

    protected inline fun <reified T, reified A0, reified A1, reified R> checkWithExceptionExecutionMatches(
        method: KFunction3<T, A0, A1, R>,
        vararg analysisResultsMatchers: (T, A0, A1, Result<R>) -> Boolean,
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
    ) {
        checkWithExceptionMatches(
            method,
            analysisResultsMatchers = analysisResultsMatchers,
            coverageChecker = coverageChecker,
            checkMode = CheckMode.MATCH_EXECUTIONS
        )
    }

    protected inline fun <reified T, reified A0, reified A1, reified R> checkWithExceptionMatches(
        method: KFunction3<T, A0, A1, R>,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
        vararg analysisResultsMatchers: (T, A0, A1, Result<R>) -> Boolean,
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
        checkMode: CheckMode,
    ) {
        internalCheck(
            target = method,
            analysisResultsNumberMatcher,
            analysisResultsMatchers,
            extractValuesToCheck = { test: JcTest ->
                val values = test.takeAllParameters(method, allParametersCount = 3)
                test.result.let { values += it }
                values
            },
            // We don't check type for the result here
            expectedTypesForExtractedValues = arrayOf(T::class, A0::class, A1::class),
            checkMode = checkMode,
            coverageChecker
        )
    }

    protected inline fun <reified T, reified A0, reified A1, reified A2, reified R> checkWithExceptionPropertiesMatches(
        method: KFunction4<T, A0, A1, A2, R>,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher,
        vararg analysisResultsMatchers: (T, A0, A1, A2, Result<R>) -> Boolean,
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
    ) {
        checkWithExceptionMatches(
            method,
            analysisResultsNumberMatcher,
            *analysisResultsMatchers,
            coverageChecker = coverageChecker,
            checkMode = CheckMode.MATCH_PROPERTIES
        )
    }

    protected inline fun <reified T, reified A0, reified A1, reified A2, reified R> checkWithExceptionExecutionMatches(
        method: KFunction4<T, A0, A1, A2, R>,
        vararg analysisResultsMatchers: (T, A0, A1, A2, Result<R>) -> Boolean,
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
    ) {
        checkWithExceptionMatches(
            method,
            analysisResultsMatchers = analysisResultsMatchers,
            coverageChecker = coverageChecker,
            checkMode = CheckMode.MATCH_EXECUTIONS
        )
    }

    protected inline fun <reified T, reified A0, reified A1, reified A2, reified R> checkWithExceptionMatches(
        method: KFunction4<T, A0, A1, A2, R>,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
        vararg analysisResultsMatchers: (T, A0, A1, A2, Result<R>) -> Boolean,
        noinline coverageChecker: (JcClassCoverage) -> Boolean = { _ -> true }, // TODO remove it
        checkMode: CheckMode,
    ) {
        internalCheck(
            target = method,
            analysisResultsNumberMatcher,
            analysisResultsMatchers,
            extractValuesToCheck = { test: JcTest ->
                val values = test.takeAllParameters(method, allParametersCount = 4)
                test.result.let { values += it }
                values
            },
            // We don't check type for the result here
            expectedTypesForExtractedValues = arrayOf(T::class, A0::class, A1::class, A2::class),
            checkMode = checkMode,
            coverageChecker
        )
    }

    // endregion

    protected fun JcTest.takeAllParameters(
        method: KFunction<*>,
        allParametersCount: Int,
    ): MutableList<Any?> {
        val values = mutableListOf<Any?>()
        if (method.instanceParameter != null) {
            requireNotNull(before.thisInstance)
            values += before.thisInstance
        } else {
            require(before.thisInstance == null)
        }
        values.addAll(before.parameters.take(allParametersCount - values.size)) // add remaining arguments
        return values
    }

    private val cp = JacoDBContainer(samplesKey).cp

    private val testResolver = JcTestResolver()

    override val typeTransformer: (Any?) -> KClass<*>? = { value -> value?.let { it::class } }
    override val checkType: (KClass<*>?, KClass<*>?) -> Boolean =
        { expected, actual -> actual == null || expected != null && actual.isSubclassOf(expected) }

    override val runner: (KFunction<*>) -> List<JcTest> = { method ->
        val declaringClassName = requireNotNull(method.javaMethod?.declaringClass?.name)
        val jcClass = cp.findClass(declaringClassName).toType()
        val jcMethod = jcClass.declaredMethods.first { it.name == method.name }

        val machine = JcMachine(cp)
        val states = machine.analyze(jcMethod)

        states.map { testResolver.resolve(jcMethod, it) }
    }

    override val coverageRunner: (List<JcTest>) -> JcClassCoverage = { _ ->
        JcClassCoverage(visitedStmts = emptySet())
    }
}