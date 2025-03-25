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
import org.usvm.api.TsValue
import org.usvm.machine.TsMachine
import org.usvm.machine.TsOptions
import org.usvm.test.util.TestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import kotlin.reflect.KClass
import kotlin.time.Duration

typealias CoverageChecker = (TsMethodCoverage) -> Boolean

@TestInstance(PER_CLASS)
abstract class TsMethodTestRunner : TestRunner<TsTest, EtsMethod, EtsType?, TsMethodCoverage>() {

    protected abstract val scene: EtsScene

    protected fun loadSampleScene(
        className: String,
        useArkAnalyzerTypeInference: Boolean = false,
    ): EtsScene {
        val name = "$className.ts"
        val path = getResourcePath("/samples/$name")
        val file = loadEtsFileAutoConvert(
            path,
            useArkAnalyzerTypeInference = if (useArkAnalyzerTypeInference) 1 else null
        )
        return EtsScene(listOf(file))
    }

    protected fun getMethod(className: String, methodName: String): EtsMethod {
        return scene
            .projectAndSdkClasses.single { it.name == className }
            .methods.singleOrNull { it.name == methodName }
            ?: error("No such method $methodName in $className found")
    }

    protected val doNotCheckCoverage: CoverageChecker = { _ -> true }

    protected inline fun <reified R : TsValue> discoverProperties(
        method: EtsMethod,
        vararg analysisResultMatchers: (R) -> Boolean,
        invariants: Array<(R) -> Boolean> = emptyArray(),
        noinline coverageChecker: CoverageChecker = doNotCheckCoverage,
    ) {
        internalCheck(
            target = method,
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            analysisResultsMatchers = analysisResultMatchers,
            invariants = invariants,
            extractValuesToCheck = { r -> r.before.parameters + r.returnValue },
            expectedTypesForExtractedValues = arrayOf(typeTransformer(R::class)),
            checkMode = CheckMode.MATCH_PROPERTIES,
            coverageChecker = coverageChecker
        )
    }

    protected inline fun <reified T : TsValue, reified R : TsValue> discoverProperties(
        method: EtsMethod,
        vararg analysisResultMatchers: (T, R) -> Boolean,
        invariants: Array<(T, R) -> Boolean> = emptyArray(),
        noinline coverageChecker: CoverageChecker = doNotCheckCoverage,
    ) {
        internalCheck(
            target = method,
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            analysisResultsMatchers = analysisResultMatchers,
            invariants = invariants,
            extractValuesToCheck = { r -> r.before.parameters + r.returnValue },
            expectedTypesForExtractedValues = arrayOf(typeTransformer(T::class), typeTransformer(R::class)),
            checkMode = CheckMode.MATCH_PROPERTIES,
            coverageChecker = coverageChecker
        )
    }

    protected inline fun <reified T1 : TsValue, reified T2 : TsValue, reified R : TsValue> discoverProperties(
        method: EtsMethod,
        vararg analysisResultMatchers: (T1, T2, R) -> Boolean,
        invariants: Array<(T1, T2, R) -> Boolean> = emptyArray(),
        noinline coverageChecker: CoverageChecker = doNotCheckCoverage,
    ) {
        internalCheck(
            target = method,
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
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

    protected inline fun <reified T1 : TsValue, reified T2 : TsValue, reified T3 : TsValue, reified R : TsValue> discoverProperties(
        method: EtsMethod,
        vararg analysisResultMatchers: (T1, T2, T3, R) -> Boolean,
        invariants: Array<(T1, T2, T3, R) -> Boolean> = emptyArray(),
        noinline coverageChecker: CoverageChecker = doNotCheckCoverage,
    ) {
        internalCheck(
            target = method,
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
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

    protected inline fun <reified T1 : TsValue, reified T2 : TsValue, reified T3 : TsValue, reified T4 : TsValue, reified R : TsValue> discoverProperties(
        method: EtsMethod,
        vararg analysisResultMatchers: (T1, T2, T3, T4, R) -> Boolean,
        invariants: Array<(T1, T2, T3, T4, R) -> Boolean> = emptyArray(),
        noinline coverageChecker: CoverageChecker = doNotCheckCoverage,
    ) {
        internalCheck(
            target = method,
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
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
    override val checkType: (EtsType?, EtsType?) -> Boolean = { _, _ -> true }

    override val typeTransformer: (Any?) -> EtsType = {
        requireNotNull(it) { "Raw null value should not be passed here" }

        /*
            Both KClass and TsObject instances come here because
            only KClass<TsObject> is available to match different objects.
            However, this method is also used in parent TestRunner class
            and passes here TsObject instances. So this check on current level is required.
        */
        val klass = if (it is KClass<*>) it else it::class
        when (klass) {
            TsValue::class -> EtsAnyType
            TsValue.TsAny::class -> EtsAnyType

            TsValue.TsArray::class -> {
                EtsArrayType(EtsAnyType, dimensions = 1) // TODO incorrect
            }

            TsValue.TsClass::class -> {
                // TODO incorrect
                val signature = EtsClassSignature(it.toString(), EtsFileSignature.UNKNOWN)
                EtsClassType(signature)
            }

            TsValue.TsBoolean::class -> EtsBooleanType
            TsValue.TsString::class -> EtsStringType
            TsValue.TsNumber::class -> EtsNumberType
            TsValue.TsNumber.TsDouble::class -> EtsNumberType
            TsValue.TsNumber.TsInteger::class -> EtsNumberType
            TsValue.TsUndefined::class -> EtsUndefinedType
            // TODO: EtsUnknownType is mock up here. Correct implementation required.
            TsValue.TsObject::class -> EtsUnknownType
            // For untyped tests, not to limit objects serialized from models after type coercion.
            TsValue.TsUnknown::class -> EtsUnknownType
            TsValue.TsNull::class -> EtsNullType

            TsValue.TsException::class -> {
                // TODO incorrect
                val signature = EtsClassSignature("Exception", EtsFileSignature.UNKNOWN)
                EtsClassType(signature)
            }

            else -> error("Unsupported type: $klass")
        }
    }

    override val runner: (EtsMethod, UMachineOptions) -> List<TsTest> = { method, options ->
        val tsMachineOptions = TsOptions()
        TsMachine(scene, options, tsMachineOptions).use { machine ->
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
