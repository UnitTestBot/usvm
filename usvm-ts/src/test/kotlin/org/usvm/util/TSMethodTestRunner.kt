package org.usvm.util

import org.jacodb.ets.base.EtsAnyType
import org.jacodb.ets.base.EtsBooleanType
import org.jacodb.ets.base.EtsNumberType
import org.jacodb.ets.base.EtsStringType
import org.jacodb.ets.base.EtsType
import org.jacodb.ets.base.EtsUndefinedType
import org.jacodb.ets.dto.EtsFileDto
import org.jacodb.ets.dto.convertToEtsFile
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsMethod
import org.usvm.NoCoverage
import org.usvm.PathSelectionStrategy
import org.usvm.TSMachine
import org.usvm.TSMethodCoverage
import org.usvm.TSObject
import org.usvm.TSTest
import org.usvm.UMachineOptions
import org.usvm.test.util.TestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

typealias CoverageChecker = (TSMethodCoverage) -> Boolean

open class TSMethodTestRunner : TestRunner<TSTest, MethodDescriptor, EtsType?, TSMethodCoverage>() {

    protected val globalClassName = "_DEFAULT_ARK_CLASS"

    protected val doNotCheckCoverage: CoverageChecker = { _ -> true }

    protected inline fun <reified R : TSObject> discoverProperties(
        methodIdentifier: MethodDescriptor,
        vararg analysisResultMatchers: (R?) -> Boolean,
        invariants: Array<out Function<Boolean>> = emptyArray(),
        noinline coverageChecker: CoverageChecker = doNotCheckCoverage,
    ) {
        internalCheck(
            target = methodIdentifier,
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            analysisResultsMatchers = analysisResultMatchers,
            invariants = invariants,
            extractValuesToCheck = { r -> r.parameters + r.resultValue },
            expectedTypesForExtractedValues = arrayOf(typeTransformer(R::class)),
            checkMode = CheckMode.MATCH_PROPERTIES,
            coverageChecker = coverageChecker
        )
    }

    protected inline fun <reified T : TSObject, reified R : TSObject> discoverProperties(
        methodIdentifier: MethodDescriptor,
        vararg analysisResultMatchers: (T, R?) -> Boolean,
        invariants: Array<out Function<Boolean>> = emptyArray(),
        noinline coverageChecker: CoverageChecker = doNotCheckCoverage,
    ) {
        internalCheck(
            target = methodIdentifier,
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            analysisResultsMatchers = analysisResultMatchers,
            invariants = invariants,
            extractValuesToCheck = { r -> r.parameters + r.resultValue },
            expectedTypesForExtractedValues = arrayOf(typeTransformer(T::class), typeTransformer(R::class)),
            checkMode = CheckMode.MATCH_PROPERTIES,
            coverageChecker = coverageChecker
        )
    }

    protected inline fun <reified T1 : TSObject, reified T2 : TSObject, reified R : TSObject> discoverProperties(
        methodIdentifier: MethodDescriptor,
        vararg analysisResultMatchers: (T1, T2, R?) -> Boolean,
        invariants: Array<out Function<Boolean>> = emptyArray(),
        noinline coverageChecker: CoverageChecker = doNotCheckCoverage,
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
            coverageChecker = coverageChecker
        )
    }

    /*
        For now type checks are disabled for development purposes

        See https://github.com/UnitTestBot/usvm/issues/203
     */
    override val checkType: (EtsType?, EtsType?) -> Boolean
        get() = { _, _ -> true }

    override val typeTransformer: (Any?) -> EtsType
        get() = {
            // Both KClass and TSObject instances come here
            val temp = if (it is KClass<*> || it == null) it else it::class

            when (temp) {
                TSObject.AnyObject::class -> EtsAnyType
                TSObject.Array::class -> TODO()
                TSObject.Boolean::class -> EtsBooleanType
                TSObject.Class::class -> TODO()
                TSObject.String::class -> EtsStringType
                TSObject.TSNumber::class -> EtsNumberType
                TSObject.TSNumber.Double::class -> EtsNumberType
                TSObject.TSNumber.Integer::class -> EtsNumberType
                TSObject.UndefinedObject::class -> EtsUndefinedType
                else -> error("Should not be called")
            }
        }

    private fun getProject(fileName: String): EtsFile {
        val jsonWithoutExtension = "/ir/$fileName.json"
        val sampleFilePath = javaClass.getResourceAsStream(jsonWithoutExtension)
            ?: error("Resource not found: $jsonWithoutExtension")

        val etsFileDto = EtsFileDto.loadFromJson(sampleFilePath)

        return convertToEtsFile(etsFileDto)
    }

    private fun EtsFile.getMethodByDescriptor(desc: MethodDescriptor): EtsMethod {
        val cls = classes.find { it.name == desc.className }
            ?: error("No class ${desc.className} in project $name")

        return cls.methods.find { it.name == desc.methodName && it.parameters.size == desc.argumentsNumber }
            ?: error("No method matching $desc found in $name")
    }

    override val runner: (MethodDescriptor, UMachineOptions) -> List<TSTest>
        get() = { id, options ->
            val project = getProject(id.fileName)
            val method = project.getMethodByDescriptor(id)

            TSMachine(project, options).use { machine ->
                val states = machine.analyze(listOf(method))
                states.map { state ->
                    val resolver = TSTestResolver()
                    resolver.resolve(method, state).also { println(it) }
                }
            }
        }

    override val coverageRunner: (List<TSTest>) -> TSMethodCoverage = { _ -> NoCoverage }

    override var options: UMachineOptions = UMachineOptions(
        pathSelectionStrategies = listOf(PathSelectionStrategy.CLOSEST_TO_UNCOVERED_RANDOM),
        exceptionsPropagation = true,
        timeout = 60_000.milliseconds,
        stepsFromLastCovered = 3500L,
        solverTimeout = Duration.INFINITE, // we do not need the timeout for a solver in tests
        typeOperationsTimeout = Duration.INFINITE, // we do not need the timeout for type operations in tests
    )
}


data class MethodDescriptor(
    val fileName: String,
    val className: String,
    val methodName: String,
    val argumentsNumber: Int,
)
