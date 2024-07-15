package org.usvm.samples

import org.jacodb.ets.base.EtsType
import org.usvm.TSMethodCoverage
import org.usvm.TSTest
import org.usvm.UMachineOptions
import org.usvm.test.util.TestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

open class TSMethodTestRunner : TestRunner<TSTest, MethodDescriptor, EtsType?, TSMethodCoverage>() {

    protected inline fun <reified R> discoverProperties(
        methodIdentifier: MethodDescriptor,
        vararg analysisResultMatchers: (R?) -> Boolean,
        invariants: Array<out Function<Boolean>> = emptyArray(),
    ) {
        internalCheck(
            target = methodIdentifier,
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            analysisResultsMatchers = analysisResultMatchers,
            invariants = invariants,
            extractValuesToCheck = { r -> r.parameters + r.resultValue },
            expectedTypesForExtractedValues = arrayOf(typeTransformer(R::class)),
            checkMode = CheckMode.MATCH_PROPERTIES,
            coverageChecker = { _ -> true }
        )
    }

    protected inline fun <reified T, reified R> discoverProperties(
        methodIdentifier: MethodDescriptor,
        vararg analysisResultMatchers: (T, R?) -> Boolean,
        invariants: Array<out Function<Boolean>> = emptyArray(),
    ) {
        internalCheck(
            target = methodIdentifier,
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            analysisResultsMatchers = analysisResultMatchers,
            invariants = invariants,
            extractValuesToCheck = { r -> r.parameters + r.resultValue },
            expectedTypesForExtractedValues = arrayOf(typeTransformer(T::class), typeTransformer(R::class)),
            checkMode = CheckMode.MATCH_PROPERTIES,
            coverageChecker = { _ -> true }
        )
    }

    protected inline fun <reified T1, reified T2, reified R> discoverProperties(
        methodIdentifier: MethodDescriptor,
        vararg analysisResultMatchers: (T1, T2, R?) -> Boolean,
        invariants: Array<out Function<Boolean>> = emptyArray(),
    ) {
        internalCheck(
            target = methodIdentifier,
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            analysisResultsMatchers = analysisResultMatchers,
            invariants = invariants,
            extractValuesToCheck = { r -> r.parameters + r.resultValue },
            expectedTypesForExtractedValues = arrayOf(
                typeTransformer(T1::class),
                typeTransformer(T2::class),
                typeTransformer(R::class)
            ),
            checkMode = CheckMode.MATCH_PROPERTIES,
            coverageChecker = { _ -> true }
        )
    }

    protected inline fun <reified T1, reified T2, reified T3, reified R> discoverProperties(
        methodIdentifier: MethodDescriptor,
        vararg analysisResultMatchers: (T1, T2, T3, R?) -> Boolean,
        invariants: Array<out Function<Boolean>> = emptyArray(),
    ) {
        internalCheck(
            target = methodIdentifier,
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            analysisResultsMatchers = analysisResultMatchers,
            invariants = invariants,
            extractValuesToCheck = { r -> r.parameters + r.resultValue },
            expectedTypesForExtractedValues = arrayOf(
                typeTransformer(T1::class),
                typeTransformer(T2::class),
                typeTransformer(T3::class),
                typeTransformer(R::class)
            ),
            checkMode = CheckMode.MATCH_PROPERTIES,
            coverageChecker = { _ -> true }
        )
    }

    protected inline fun <reified T1, reified T2, reified T3, reified T4, reified R> discoverProperties(
        methodIdentifier: MethodDescriptor,
        vararg analysisResultMatchers: (T1, T2, T3, T4, R?) -> Boolean,
        invariants: Array<out Function<Boolean>> = emptyArray(),
    ) {
        internalCheck(
            target = methodIdentifier,
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            analysisResultsMatchers = analysisResultMatchers,
            invariants = invariants,
            extractValuesToCheck = { r -> r.parameters + r.resultValue },
            expectedTypesForExtractedValues = arrayOf(
                typeTransformer(T1::class),
                typeTransformer(T2::class),
                typeTransformer(T3::class),
                typeTransformer(T4::class),
                typeTransformer(R::class)
            ),
            checkMode = CheckMode.MATCH_PROPERTIES,
            coverageChecker = { _ -> true }
        )
    }

    override val typeTransformer: (Any?) -> EtsType
        get() = TODO()

    @Suppress("UNUSED_ANONYMOUS_PARAMETER")
    override val checkType: (EtsType?, EtsType?) -> Boolean
        get() = TODO()

    override val runner: (MethodDescriptor, UMachineOptions) -> List<TSTest>
        get() = TODO()

    override val coverageRunner: (List<TSTest>) -> TSMethodCoverage = TODO()

    override var options: UMachineOptions = TODO()
}

data class MethodDescriptor(
    val className: String,
    val methodName: String,
    val argumentsNumber: Int,
)
