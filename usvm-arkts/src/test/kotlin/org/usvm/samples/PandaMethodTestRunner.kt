package org.usvm.samples

import org.jacodb.panda.dynamic.api.PandaAnyType
import org.jacodb.panda.dynamic.api.PandaType
import org.jacodb.panda.dynamic.parser.IRParser
import org.jacodb.panda.dynamic.parser.TSParser
import org.usvm.CoverageZone
import org.usvm.PathSelectionStrategy
import org.usvm.UMachineOptions
import org.usvm.machine.PandaExecutionResult
import org.usvm.machine.PandaMachine
import org.usvm.test.util.TestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private typealias FileName = String
private typealias MethodName = String
private typealias ArgsNumber = Int
private typealias Coverage = Int
private typealias MethodIdentifier = Triple<FileName, MethodName, ArgsNumber>

open class PandaMethodTestRunner
    : TestRunner<PandaExecutionResult, MethodIdentifier, PandaType?, Coverage>() {

    protected fun discoverProperties(
        methodIdentifier: MethodIdentifier,
        analysisResultMatchers: Array<out Function<Boolean>>,
        invariants: Array<out Function<Boolean>> = emptyArray(),
    ) {
        internalCheck(
            target = methodIdentifier,
            analysisResultsNumberMatcher = ignoreNumberOfAnalysisResults,
            analysisResultsMatchers = analysisResultMatchers,
            invariants = invariants,
            extractValuesToCheck = { _ -> emptyList() },
            expectedTypesForExtractedValues = emptyArray(),
            checkMode = CheckMode.MATCH_PROPERTIES,
            coverageChecker = { _ -> true }
        )
    }

    override val typeTransformer: (Any?) -> PandaType
        get() = { _ -> PandaAnyType } // TODO("Not yet implemented")

    @Suppress("UNUSED_ANONYMOUS_PARAMETER")
    override val checkType: (PandaType?, PandaType?) -> Boolean
        get() = { expected, actual -> true } // TODO("Not yet implemented")

    override val runner: (MethodIdentifier, UMachineOptions) -> List<PandaExecutionResult>
        get() = { id, options ->
            // TODO Automatic parser?????
            val jsonWithoutExtension = "/samples/${id.first}.json"
            val tsWithoutExtension = "/samples/${id.first}.ts"
            // TODO: Make tsFile parsing here optional
            val sampleTsFilePath = javaClass.getResource(tsWithoutExtension)?.toURI()!!
            val sampleFilePath = javaClass.getResource(jsonWithoutExtension)?.path ?: ""

            val tsParser = TSParser(sampleTsFilePath)
            val tsFunctions = tsParser.collectFunctions()
            val parser = IRParser(sampleFilePath, tsFunctions)
            val project = parser.getProject()

            // TODO class name??????
            val method = project.findMethodOrNull(id.second, "GLOBAL") ?: error("TODO")


            PandaMachine(project, options).use {
                val states = it.analyze(listOf(method))
                states.map {
                    println(it)
                    PandaExecutionResult() // TODO transform
                }
            }
        }

    override val coverageRunner: (List<PandaExecutionResult>) -> Coverage
        get() = { _ -> 0 } // TODO("Not yet implemented")

    override var options: UMachineOptions = UMachineOptions(
        pathSelectionStrategies = listOf(PathSelectionStrategy.CLOSEST_TO_UNCOVERED_RANDOM),
        coverageZone = CoverageZone.TRANSITIVE,
        exceptionsPropagation = true,
        timeout = 60_000.milliseconds,
        stepsFromLastCovered = 3500L,
        solverTimeout = Duration.INFINITE, // we do not need the timeout for a solver in tests
        typeOperationsTimeout = Duration.INFINITE, // we do not need the timeout for type operations in tests
    )
}
