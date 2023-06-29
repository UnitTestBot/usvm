package org.usvm.samples

import org.usvm.interpreter.ConcretePythonInterpreter
import org.usvm.interpreter.PythonAnalysisResult
import org.usvm.interpreter.PythonMachine
import org.usvm.language.PythonCallable
import org.usvm.language.PythonInt
import org.usvm.language.PythonProgram
import org.usvm.language.PythonType
import org.usvm.test.util.TestRunner
import org.usvm.test.util.checkers.AnalysisResultsNumberMatcher
import java.io.File

open class PythonTestRunner(sourcePath: String) : TestRunner<PythonTest, PythonCallable, PythonType, PythonCoverage>() {
    private val testSources = File(PythonTestRunner::class.java.getResource(sourcePath)!!.file).readText()
    private val machine = PythonMachine(PythonProgram(testSources)) { pythonObject ->
        ConcretePythonInterpreter.getPythonObjectRepr(pythonObject)
    }
    override val typeTransformer: (Any?) -> PythonType
        get() = { _ -> PythonInt }
    override val checkType: (PythonType, PythonType) -> Boolean
        get() = { _, _ -> true }
    override val runner: (PythonCallable) -> List<PythonTest>
        get() = { callable ->
            val results: MutableList<PythonAnalysisResult<String>> = mutableListOf()
            machine.analyze(callable, results)
            results
        }
    override val coverageRunner: (List<PythonTest>) -> PythonCoverage
        get() = { _ -> PythonCoverage(0) }

    private inline fun <reified FUNCTION_TYPE : Function<Boolean>> createCheckReprs():
                (PythonCallable, AnalysisResultsNumberMatcher, List<FUNCTION_TYPE>, List<FUNCTION_TYPE>) -> Unit = {
        target: PythonCallable,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher,
        invariants: List<FUNCTION_TYPE>,
        propertiesToDiscover: List<FUNCTION_TYPE> ->
        internalCheck(
            target,
            analysisResultsNumberMatcher,
            propertiesToDiscover.toTypedArray(),
            invariants.toTypedArray(),
            { pythonTest -> pythonTest.inputValues.map { it.reprFromPythonObject } + pythonTest.result },
            (target.signature.map { PythonInt } + PythonInt).toTypedArray(),
            CheckMode.MATCH_PROPERTIES,
            { true }
        )
    }

    protected val checkReprs3 = createCheckReprs<(String, String, String, String) -> Boolean>()
}

typealias PythonTest = PythonAnalysisResult<String>
data class PythonCoverage(val int: Int)