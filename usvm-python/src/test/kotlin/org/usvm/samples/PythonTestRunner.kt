package org.usvm.samples

import org.usvm.interpreter.*
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

    private fun compareWithConcreteRun(
        target: PythonCallable,
        test: PythonTest,
        check: (PythonObject) -> Boolean
    ): Boolean {
        val namespace = ConcretePythonInterpreter.getNewNamespace()
        ConcretePythonInterpreter.concreteRun(namespace, testSources)
        val functionRef = target.reference(namespace)
        val converter = ConverterToPythonObject(namespace)
        val args = test.inputValues.map { converter.convert(it.asUExpr, it.type)!! }
        val concreteResult = ConcretePythonInterpreter.concreteRunOnFunctionRef(namespace, functionRef, args)
        return check(concreteResult)
    }

    private inline fun <reified FUNCTION_TYPE : Function<Boolean>> createCheckReprsWithConcreteRun(concreteRun: Boolean = true):
                (PythonCallable, AnalysisResultsNumberMatcher, List<FUNCTION_TYPE>, List<FUNCTION_TYPE>, (PythonTest, PythonObject) -> Boolean) -> Unit = {
            target: PythonCallable,
            analysisResultsNumberMatcher: AnalysisResultsNumberMatcher,
            invariants: List<FUNCTION_TYPE>,
            propertiesToDiscover: List<FUNCTION_TYPE>,
            compareConcolicAndConcrete: (PythonTest, PythonObject) -> Boolean ->
        val onAnalysisResult = { pythonTest: PythonTest ->
            val result = pythonTest.inputValues.map { it.reprFromPythonObject } + pythonTest.result
            if (concreteRun) {
                require(compareWithConcreteRun(target, pythonTest) { compareConcolicAndConcrete(pythonTest, it) }) {
                    "Error in CPython patch: concrete and concolic results differ"
                }
            }
            result
        }
        internalCheck(
            target,
            analysisResultsNumberMatcher,
            propertiesToDiscover.toTypedArray(),
            invariants.toTypedArray(),
            onAnalysisResult,
            (target.signature.map { PythonInt } + PythonInt).toTypedArray(),
            CheckMode.MATCH_PROPERTIES,
            coverageChecker = { true }
        )
    }

    private inline fun <reified FUNCTION_TYPE : Function<Boolean>> createCheckReprs():
                (PythonCallable, AnalysisResultsNumberMatcher, List<FUNCTION_TYPE>, List<FUNCTION_TYPE>) -> Unit = {
        target: PythonCallable,
        analysisResultsNumberMatcher: AnalysisResultsNumberMatcher,
        invariants: List<FUNCTION_TYPE>,
        propertiesToDiscover: List<FUNCTION_TYPE> ->
        createCheckReprsWithConcreteRun<FUNCTION_TYPE>(concreteRun = false)(target, analysisResultsNumberMatcher, invariants, propertiesToDiscover) { _, _ -> true }
    }

    protected val checkReprs3 = createCheckReprs<(String, String, String, String) -> Boolean>()
    protected val checkReprs3WithConcreteRun = createCheckReprsWithConcreteRun<(String, String, String, String) -> Boolean>()
}

typealias PythonTest = PythonAnalysisResult<String>
data class PythonCoverage(val int: Int)