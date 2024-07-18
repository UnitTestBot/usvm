package org.usvm.util

import org.jacodb.ets.base.EtsType
import org.jacodb.ets.dto.EtsFileDto
import org.jacodb.ets.dto.convertToEtsFile
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsMethod
import org.usvm.PathSelectionStrategy
import org.usvm.TSMachine
import org.usvm.TSMethodCoverage
import org.usvm.TSTest
import org.usvm.UMachineOptions
import org.usvm.test.util.TestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

open class TSMethodTestRunner : TestRunner<TSTest, MethodDescriptor, EtsType?, TSMethodCoverage>() {

    protected val globalClassName = "_DEFAULT_ARK_CLASS"

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
        get() = TODO("Not yet implemented")

    override val checkType: (EtsType?, EtsType?) -> Boolean
        get() = TODO("Not yet implemented")

    private fun getProject(fileName: String): EtsFile {
        val jsonWithoutExtension = "/ir/${fileName}.json"
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

    override val coverageRunner: (List<TSTest>) -> TSMethodCoverage = TODO()

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
