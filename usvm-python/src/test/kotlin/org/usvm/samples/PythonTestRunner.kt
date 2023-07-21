package org.usvm.samples

import org.usvm.UMachineOptions
import org.usvm.interpreter.*
import org.usvm.language.*
import org.usvm.language.types.PythonType
import org.usvm.language.types.pythonInt
import org.usvm.test.util.TestRunner
import org.usvm.test.util.checkers.AnalysisResultsNumberMatcher
import java.io.File

open class PythonTestRunner(sourcePath: String) : TestRunner<PythonTest, PythonUnpinnedCallable, PythonType, PythonCoverage>() {
    private val testSources = File(PythonTestRunner::class.java.getResource(sourcePath)!!.file).readText()
    private val machine = PythonMachine(PythonProgram(testSources)) { pythonObject ->
        PythonObjectInfo(
            ConcretePythonInterpreter.getPythonObjectRepr(pythonObject),
            ConcretePythonInterpreter.getPythonObjectTypeName(pythonObject)
        )
    }
    override val typeTransformer: (Any?) -> PythonType
        get() = { _ -> pythonInt }
    override val checkType: (PythonType, PythonType) -> Boolean
        get() = { _, _ -> true }
    override val runner: (PythonUnpinnedCallable, UMachineOptions) -> List<PythonTest>
        get() = { callable, _ ->
            val results: MutableList<PythonTest> = mutableListOf()
            machine.analyze(callable, results)
            results
        }
    override val coverageRunner: (List<PythonTest>) -> PythonCoverage
        get() = { _ -> PythonCoverage(0) }

    private fun compareWithConcreteRun(
        target: PythonUnpinnedCallable,
        test: PythonTest,
        check: (PythonObject) -> Boolean
    ): Boolean {
        val namespace = ConcretePythonInterpreter.getNewNamespace()
        ConcretePythonInterpreter.concreteRun(namespace, testSources)
        val functionRef = target.reference(namespace)
        val converter = test.inputValueConverter
        converter.restart()
        val args = test.inputValues.map { converter.convert(it.asUExpr) }
        val concreteResult = ConcretePythonInterpreter.concreteRunOnFunctionRef(functionRef, args)
        return check(concreteResult)
    }

    private inline fun <reified FUNCTION_TYPE : Function<Boolean>> createCheckWithConcreteRun(concreteRun: Boolean = true):
                (PythonUnpinnedCallable, AnalysisResultsNumberMatcher, (PythonTest, PythonObject) -> Boolean, List<FUNCTION_TYPE>, List<FUNCTION_TYPE>) -> Unit =
        { target: PythonUnpinnedCallable,
          analysisResultsNumberMatcher: AnalysisResultsNumberMatcher,
          compareConcolicAndConcrete: (PythonTest, PythonObject) -> Boolean,
          invariants: List<FUNCTION_TYPE>,
          propertiesToDiscover: List<FUNCTION_TYPE> ->
            val onAnalysisResult = { pythonTest: PythonTest ->
                val result = pythonTest.inputValues.map { it.reprFromPythonObject } + (pythonTest.result as? Success)?.output
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
                (target.signature.map { pythonInt } + pythonInt).toTypedArray(),
                CheckMode.MATCH_PROPERTIES,
                coverageChecker = { true }
            )
        }

    private inline fun <reified FUNCTION_TYPE : Function<Boolean>> createCheck():
                (PythonUnpinnedCallable, AnalysisResultsNumberMatcher, List<FUNCTION_TYPE>, List<FUNCTION_TYPE>) -> Unit =
        { target: PythonUnpinnedCallable,
          analysisResultsNumberMatcher: AnalysisResultsNumberMatcher,
          invariants: List<FUNCTION_TYPE>,
          propertiesToDiscover: List<FUNCTION_TYPE> ->
            createCheckWithConcreteRun<FUNCTION_TYPE>(concreteRun = false)(
                target,
                analysisResultsNumberMatcher,
                { _, _ -> true },
                invariants,
                propertiesToDiscover
            )
        }

    protected val check1 = createCheck<(PythonObjectInfo, PythonObjectInfo?) -> Boolean>()
    protected val check1WithConcreteRun =
        createCheckWithConcreteRun<(PythonObjectInfo, PythonObjectInfo?) -> Boolean>()

    protected val check2 = createCheck<(PythonObjectInfo, PythonObjectInfo, PythonObjectInfo?) -> Boolean>()
    protected val check2WithConcreteRun =
        createCheckWithConcreteRun<(PythonObjectInfo, PythonObjectInfo, PythonObjectInfo?) -> Boolean>()

    protected val check3WithConcreteRun =
        createCheckWithConcreteRun<(PythonObjectInfo, PythonObjectInfo, PythonObjectInfo, PythonObjectInfo?) -> Boolean>()

    protected val compareConcolicAndConcreteReprs:
                (PythonTest, PythonObject) -> Boolean = { testFromConcolic, concreteResult ->
        (testFromConcolic.result as? Success)?.let {
            it.output.repr == ConcretePythonInterpreter.getPythonObjectRepr(concreteResult)
        } ?: true
    }

    protected fun constructFunction(name: String, signature: List<PythonType>): PythonUnpinnedCallable =
        PythonUnpinnedCallable.constructCallableFromName(signature, name)
}

data class PythonObjectInfo(
    val repr: String,
    val typeName: String
)

typealias PythonTest = PythonAnalysisResult<PythonObjectInfo>

data class PythonCoverage(val int: Int)