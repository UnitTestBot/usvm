package org.usvm.samples

import TestOptions
import org.jacodb.panda.dynamic.api.PandaAnyType
import org.jacodb.panda.dynamic.api.PandaInst
import org.jacodb.panda.dynamic.api.PandaProject
import org.jacodb.panda.dynamic.api.PandaType
import org.jacodb.panda.dynamic.parser.IRParser
import org.jacodb.panda.dynamic.parser.TSParser
import org.usvm.CoverageZone
import org.usvm.PathSelectionStrategy
import org.usvm.UMachineOptions
import org.usvm.machine.PandaClassCoverage
import org.usvm.machine.PandaMachine
import org.usvm.machine.PandaTest
import org.usvm.test.util.TestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import kotlin.reflect.KFunction1
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private typealias Coverage = Int

open class PandaMethodTestRunner
    : TestRunner<PandaTest, MethodDescriptor, PandaType?, PandaClassCoverage>() {

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

    protected inline fun <reified R> discoverPropertiesWithTraceVerification(
        methodIdentifier: MethodDescriptor,
        vararg analysisResultMatchers: (R?, List<PandaInst>) -> Boolean,
        invariants: Array<out Function<Boolean>> = emptyArray(),
    ) {
        internalCheck(
            target = methodIdentifier,
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            analysisResultsMatchers = analysisResultMatchers,
            invariants = invariants,
            extractValuesToCheck = { r -> r.parameters + r.resultValue + r.trace },
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


    override val typeTransformer: (Any?) -> PandaType
        get() = { _ -> PandaAnyType } // TODO("Not yet implemented")

    @Suppress("UNUSED_ANONYMOUS_PARAMETER")
    override val checkType: (PandaType?, PandaType?) -> Boolean
        get() = { expected, actual -> true } // TODO("Not yet implemented")

    protected fun getProject(className: String) : PandaProject {
        val jsonWithoutExtension = "/samples/${className}.json"
        val tsWithoutExtension = "/samples/${className}.ts"
        // TODO: Make tsFile parsing here optional
        val sampleTsFilePath = javaClass.getResource(tsWithoutExtension)?.toURI()!!
        val sampleFilePath = javaClass.getResource(jsonWithoutExtension)?.path ?: ""

        val tsParser = TSParser(sampleTsFilePath)
        val tsFunctions = tsParser.collectFunctions()
        val parser = IRParser(sampleFilePath, tsFunctions)
        val project = parser.getProject()

        return project
    }

    override val runner: (MethodDescriptor, UMachineOptions) -> List<PandaTest>
        get() = { id, options ->

            val project = getProject(id.className)
            // TODO class name??????
            val method = project.findMethodOrNull(id.methodName, "GLOBAL") ?: error("TODO")


            PandaMachine(project, options).use { machine ->
                val states = machine.analyze(listOf(method))
                states.map { state ->
                    val resolver = PandaTestResolver()
                    resolver.resolve(method, state).also { println(it) }
                }
            }
        }

    override val coverageRunner: (List<PandaTest>) -> PandaClassCoverage = { _ ->
        PandaClassCoverage(visitedStmts = emptySet())
    }

    override var options: UMachineOptions = UMachineOptions(
        pathSelectionStrategies = listOf(PathSelectionStrategy.CLOSEST_TO_UNCOVERED_RANDOM),
//        loopIterationLimit = 20,
        exceptionsPropagation = true,
        timeout = 60_000.milliseconds,
        stepsFromLastCovered = 3500L,
        solverTimeout = Duration.INFINITE, // we do not need the timeout for a solver in tests
        typeOperationsTimeout = Duration.INFINITE, // we do not need the timeout for type operations in tests
    )
}

data class MethodDescriptor(
    val className: String,
    val methodName: String,
    val argumentsNumber: Int,
)
