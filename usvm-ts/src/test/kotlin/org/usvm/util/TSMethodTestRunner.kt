package org.usvm.util

import org.jacodb.panda.dynamic.ets.base.EtsType
import org.jacodb.panda.dynamic.ets.dto.EtsFileDto
import org.jacodb.panda.dynamic.ets.dto.convertToEtsFile
import org.jacodb.panda.dynamic.ets.model.EtsClassSignature
import org.jacodb.panda.dynamic.ets.model.EtsFile
import org.jacodb.panda.dynamic.ets.model.EtsMethod
import org.jacodb.panda.dynamic.ets.model.EtsMethodSignature
import org.usvm.TSMachine
import org.usvm.TSMethodCoverage
import org.usvm.TSTest
import org.usvm.UMachineOptions
import org.usvm.test.util.TestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

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
        get() = TODO()

    @Suppress("UNUSED_ANONYMOUS_PARAMETER")
    override val checkType: (EtsType?, EtsType?) -> Boolean
        get() = TODO()

    private fun getProject(className: String): EtsFile {
        val jsonWithoutExtension = "/ir/${className}.json"
        val sampleFilePath = javaClass.getResource(jsonWithoutExtension)?.path
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
            val project = getProject(id.className)
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

    override var options: UMachineOptions = TODO()
}

data class MethodDescriptor(
    val className: String,
    val methodName: String,
    val argumentsNumber: Int,
)
