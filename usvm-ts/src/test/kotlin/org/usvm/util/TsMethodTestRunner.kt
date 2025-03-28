package org.usvm.util

import org.jacodb.ets.model.EtsAnyType
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsNullType
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUndefinedType
import org.jacodb.ets.model.EtsUnknownType
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.usvm.PathSelectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.NoCoverage
import org.usvm.api.TsMethodCoverage
import org.usvm.api.TsTest
import org.usvm.api.TsTestValue
import org.usvm.machine.TsMachine
import org.usvm.model.TsAnyType
import org.usvm.model.TsArrayType
import org.usvm.model.TsBooleanType
import org.usvm.model.TsClassSignature
import org.usvm.model.TsClassType
import org.usvm.model.TsFileSignature
import org.usvm.model.TsMethod
import org.usvm.model.TsNullType
import org.usvm.model.TsNumberType
import org.usvm.model.TsScene
import org.usvm.model.TsStringType
import org.usvm.model.TsType
import org.usvm.model.TsUndefinedType
import org.usvm.model.TsUnknownType
import org.usvm.test.util.TestRunner
import org.usvm.test.util.checkers.AnalysisResultsNumberMatcher
import kotlin.reflect.KClass
import kotlin.time.Duration

typealias CoverageChecker = (TsMethodCoverage) -> Boolean

@TestInstance(PER_CLASS)
abstract class TsMethodTestRunner : TestRunner<TsTest, TsMethod, TsType?, TsMethodCoverage>() {

    protected abstract val scene: TsScene

    protected fun loadSampleScene(
        className: String,
        useArkAnalyzerTypeInference: Boolean = false,
    ): TsScene {
        val name = "$className.ts"
        val path = getResourcePath("/samples/$name")
        val file = loadEtsFileAutoConvert(
            path,
            useArkAnalyzerTypeInference = if (useArkAnalyzerTypeInference) 1 else null
        )
        val scene = EtsScene(listOf(file))
        return scene.convert()
    }

    protected fun getMethod(className: String, methodName: String): TsMethod {
        return scene
            .projectAndSdkClasses.single { it.name == className }
            .methods.singleOrNull { it.name == methodName }
            ?: error("No such method $methodName in $className found")
    }

    protected val doNotCheckCoverage: CoverageChecker = { _ -> true }

    protected inline fun <reified R : TsTestValue> discoverProperties(
        method: TsMethod,
        vararg analysisResultMatchers: (R) -> Boolean,
        invariants: Array<(R) -> Boolean> = emptyArray(),
        noinline coverageChecker: CoverageChecker = doNotCheckCoverage,
    ) {
        internalCheck(
            target = method,
            analysisResultsNumberMatcher = absolutelyIgnoreNumberOfAnalysisResults,
            analysisResultsMatchers = analysisResultMatchers,
            invariants = invariants,
            extractValuesToCheck = { r -> r.before.parameters + r.returnValue },
            expectedTypesForExtractedValues = arrayOf(typeTransformer(R::class)),
            checkMode = CheckMode.MATCH_PROPERTIES,
            coverageChecker = coverageChecker
        )
    }

    protected inline fun <reified T : TsTestValue, reified R : TsTestValue> discoverProperties(
        method: TsMethod,
        vararg analysisResultMatchers: (T, R) -> Boolean,
        invariants: Array<(T, R) -> Boolean> = emptyArray(),
        noinline coverageChecker: CoverageChecker = doNotCheckCoverage,
    ) {
        internalCheck(
            target = method,
            analysisResultsNumberMatcher = absolutelyIgnoreNumberOfAnalysisResults,
            analysisResultsMatchers = analysisResultMatchers,
            invariants = invariants,
            extractValuesToCheck = { r -> r.before.parameters + r.returnValue },
            expectedTypesForExtractedValues = arrayOf(typeTransformer(T::class), typeTransformer(R::class)),
            checkMode = CheckMode.MATCH_PROPERTIES,
            coverageChecker = coverageChecker
        )
    }

    protected inline fun <reified T1 : TsTestValue, reified T2 : TsTestValue, reified R : TsTestValue> discoverProperties(
        method: TsMethod,
        vararg analysisResultMatchers: (T1, T2, R) -> Boolean,
        invariants: Array<(T1, T2, R) -> Boolean> = emptyArray(),
        noinline coverageChecker: CoverageChecker = doNotCheckCoverage,
    ) {
        internalCheck(
            target = method,
            analysisResultsNumberMatcher = absolutelyIgnoreNumberOfAnalysisResults,
            analysisResultsMatchers = analysisResultMatchers,
            invariants = invariants,
            extractValuesToCheck = { r -> r.before.parameters + r.returnValue },
            expectedTypesForExtractedValues = arrayOf(
                typeTransformer(T1::class), typeTransformer(T2::class), typeTransformer(R::class)
            ),
            checkMode = CheckMode.MATCH_PROPERTIES,
            coverageChecker = coverageChecker
        )
    }

    protected inline fun <reified T1 : TsTestValue, reified T2 : TsTestValue, reified T3 : TsTestValue, reified R : TsTestValue> discoverProperties(
        method: TsMethod,
        vararg analysisResultMatchers: (T1, T2, T3, R) -> Boolean,
        invariants: Array<(T1, T2, T3, R) -> Boolean> = emptyArray(),
        noinline coverageChecker: CoverageChecker = doNotCheckCoverage,
    ) {
        internalCheck(
            target = method,
            analysisResultsNumberMatcher = absolutelyIgnoreNumberOfAnalysisResults,
            analysisResultsMatchers = analysisResultMatchers,
            invariants = invariants,
            extractValuesToCheck = { r -> r.before.parameters + r.returnValue },
            expectedTypesForExtractedValues = arrayOf(
                typeTransformer(T1::class),
                typeTransformer(T2::class),
                typeTransformer(T3::class),
                typeTransformer(R::class)
            ),
            checkMode = CheckMode.MATCH_PROPERTIES,
            coverageChecker = coverageChecker
        )
    }

    protected inline fun <reified T1 : TsTestValue, reified T2 : TsTestValue, reified T3 : TsTestValue, reified T4 : TsTestValue, reified R : TsTestValue> discoverProperties(
        method: TsMethod,
        vararg analysisResultMatchers: (T1, T2, T3, T4, R) -> Boolean,
        invariants: Array<(T1, T2, T3, T4, R) -> Boolean> = emptyArray(),
        noinline coverageChecker: CoverageChecker = doNotCheckCoverage,
    ) {
        internalCheck(
            target = method,
            analysisResultsNumberMatcher = absolutelyIgnoreNumberOfAnalysisResults,
            analysisResultsMatchers = analysisResultMatchers,
            invariants = invariants,
            extractValuesToCheck = { r -> r.before.parameters + r.returnValue },
            expectedTypesForExtractedValues = arrayOf(
                typeTransformer(T1::class),
                typeTransformer(T2::class),
                typeTransformer(T3::class),
                typeTransformer(T4::class),
                typeTransformer(R::class)
            ),
            checkMode = CheckMode.MATCH_PROPERTIES,
            coverageChecker = coverageChecker
        )
    }

    /*
        For now type checks are disabled for development purposes

        See https://github.com/UnitTestBot/usvm/issues/203
     */
    override val checkType: (TsType?, TsType?) -> Boolean = { _, _ -> true }

    override val typeTransformer: (Any?) -> TsType = {
        requireNotNull(it) { "Raw null value should not be passed here" }

        /*
            Both KClass and TsObject instances come here because
            only KClass<TsObject> is available to match different objects.
            However, this method is also used in parent TestRunner class
            and passes here TsObject instances. So this check on current level is required.
        */
        val klass = if (it is KClass<*>) it else it::class
        when (klass) {
            TsTestValue::class -> TsAnyType
            TsTestValue.TsAny::class -> TsAnyType

            TsTestValue.TsArray::class -> {
                TsArrayType(TsAnyType, dimensions = 1) // TODO incorrect
            }

            TsTestValue.TsClass::class -> {
                // TODO incorrect
                val signature = EtsClassSignature(it.toString(), EtsFileSignature.UNKNOWN)
                EtsClassType(signature)
            }

            TsTestValue.TsBoolean::class -> TsBooleanType
            TsTestValue.TsString::class -> TsStringType
            TsTestValue.TsNumber::class -> TsNumberType
            TsTestValue.TsNumber.TsDouble::class -> TsNumberType
            TsTestValue.TsNumber.TsInteger::class -> TsNumberType
            TsTestValue.TsUndefined::class -> TsUndefinedType
            // TODO: TsUnknownType is mock up here. Correct implementation required.
            TsTestValue.TsObject::class -> TsUnknownType
            // For untyped tests, not to limit objects serialized from models after type coercion.
            TsTestValue.TsUnknown::class -> TsUnknownType
            TsTestValue.TsNull::class -> TsNullType

            TsTestValue.TsException::class -> {
                // TODO incorrect
                val signature = EtsClassSignature("Exception", EtsFileSignature.UNKNOWN)
                EtsClassType(signature)
            }

            else -> error("Unsupported type: $klass")
        }
    }

    override val runner: (TsMethod, UMachineOptions) -> List<TsTest> = { method, options ->
        TsMachine(scene, options).use { machine ->
            val states = machine.analyze(listOf(method))
            states.map { state ->
                val resolver = TsTestResolver()
                resolver.resolve(method, state)
            }
        }
    }

    override val coverageRunner: (List<TsTest>) -> TsMethodCoverage = { _ -> NoCoverage }

    override var options: UMachineOptions = UMachineOptions(
        pathSelectionStrategies = listOf(PathSelectionStrategy.CLOSEST_TO_UNCOVERED_RANDOM),
        exceptionsPropagation = true,
        timeout = Duration.INFINITE,
        stepsFromLastCovered = 3500L,
        solverTimeout = Duration.INFINITE, // we do not need the timeout for a solver in tests
        typeOperationsTimeout = Duration.INFINITE, // we do not need the timeout for type operations in tests
    )
}

val absolutelyIgnoreNumberOfAnalysisResults = AnalysisResultsNumberMatcher(
    description = "Allow any number of results",
    matcherFailedMessage = { _ -> "???" }
) { true }
