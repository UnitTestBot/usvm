package org.usvm.util

import org.jacodb.ets.base.EtsAnyType
import org.jacodb.ets.base.EtsBooleanType
import org.jacodb.ets.base.EtsClassType
import org.jacodb.ets.base.EtsNumberType
import org.jacodb.ets.base.EtsStringType
import org.jacodb.ets.base.EtsType
import org.jacodb.ets.base.EtsUndefinedType
import org.jacodb.ets.base.EtsUnknownType
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.usvm.PathSelectionStrategy
import org.usvm.UMachineOptions
import org.usvm.api.NoCoverage
import org.usvm.api.TSMethodCoverage
import org.usvm.api.TSObject
import org.usvm.api.TSTest
import org.usvm.machine.TSMachine
import org.usvm.test.util.TestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import kotlin.reflect.KClass
import kotlin.time.Duration

typealias CoverageChecker = (TSMethodCoverage) -> Boolean

@TestInstance(PER_CLASS)
abstract class TSMethodTestRunner : TestRunner<TSTest, EtsMethod, EtsType?, TSMethodCoverage>() {

    protected abstract val scene: EtsScene

    protected fun getMethod(className: String, methodName: String): EtsMethod {
        return scene
            .projectAndSdkClasses.single { it.name == className }
            .methods.singleOrNull { it.name == methodName }
            ?: error("No such method $methodName in $className found")
    }

    protected val doNotCheckCoverage: CoverageChecker = { _ -> true }

    protected inline fun <reified R : TSObject> discoverProperties(
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
            extractValuesToCheck = { r -> r.parameters + r.returnValue },
            expectedTypesForExtractedValues = arrayOf(typeTransformer(R::class)),
            checkMode = CheckMode.MATCH_PROPERTIES,
            coverageChecker = coverageChecker
        )
    }

    protected inline fun <reified T : TSObject, reified R : TSObject> discoverProperties(
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
            extractValuesToCheck = { r -> r.parameters + r.returnValue },
            expectedTypesForExtractedValues = arrayOf(typeTransformer(T::class), typeTransformer(R::class)),
            checkMode = CheckMode.MATCH_PROPERTIES,
            coverageChecker = coverageChecker
        )
    }

    protected inline fun <reified T1 : TSObject, reified T2 : TSObject, reified R : TSObject> discoverProperties(
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
            extractValuesToCheck = { r -> r.parameters + r.returnValue },
            expectedTypesForExtractedValues = arrayOf(
                typeTransformer(T1::class), typeTransformer(T2::class), typeTransformer(R::class)
            ),
            checkMode = CheckMode.MATCH_PROPERTIES,
            coverageChecker = coverageChecker
        )
    }

    protected inline fun <reified T1 : TSObject, reified T2 : TSObject, reified T3 : TSObject, reified R : TSObject> discoverProperties(
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
            extractValuesToCheck = { r -> r.parameters + r.returnValue },
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

    protected inline fun <reified T1 : TSObject, reified T2 : TSObject, reified T3 : TSObject, reified T4 : TSObject, reified R : TSObject> discoverProperties(
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
            extractValuesToCheck = { r -> r.parameters + r.returnValue },
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
            Both KClass and TSObject instances come here because
            only KClass<TSObject> is available to match different objects.
            However, this method is also used in parent TestRunner class
            and passes here TSObject instances. So this check on current level is required.
        */
        val klass = if (it is KClass<*>) it else it::class
        when (klass) {
            TSObject::class -> EtsAnyType
            TSObject.TSAny::class -> EtsAnyType
            TSObject.TSArray::class -> TODO()
            TSObject.TSBoolean::class -> EtsBooleanType
            TSObject.TSClass::class -> {
                // TODO incorrect
                val signature = EtsClassSignature(it.toString(), EtsFileSignature.DEFAULT)
                EtsClassType(signature)
            }
            TSObject.TSString::class -> EtsStringType
            TSObject.TSNumber::class -> EtsNumberType
            TSObject.TSNumber.Double::class -> EtsNumberType
            TSObject.TSNumber.Integer::class -> EtsNumberType
            TSObject.UndefinedObject::class -> EtsUndefinedType
            // TODO: EtsUnknownType is mock up here. Correct implementation required.
            TSObject.TSObject::class -> EtsUnknownType
            // For untyped tests, not to limit objects serialized from models after type coercion.
            TSObject.TSUnknown::class -> EtsUnknownType
            else -> error("Unsupported type: $klass")
        }
    }

    override val runner: (EtsMethod, UMachineOptions) -> List<TSTest> = { method, options ->
        TSMachine(scene, options).use { machine ->
            val states = machine.analyze(listOf(method))
            states.map { state ->
                val resolver = TSTestResolver(state)
                resolver.resolve(method)
            }
        }
    }

    override val coverageRunner: (List<TSTest>) -> TSMethodCoverage = { _ -> NoCoverage }

    override var options: UMachineOptions = UMachineOptions(
        pathSelectionStrategies = listOf(PathSelectionStrategy.CLOSEST_TO_UNCOVERED_RANDOM),
        exceptionsPropagation = true,
        timeout = Duration.INFINITE,
        stepsFromLastCovered = 3500L,
        solverTimeout = Duration.INFINITE, // we do not need the timeout for a solver in tests
        typeOperationsTimeout = Duration.INFINITE, // we do not need the timeout for type operations in tests
    )
}
