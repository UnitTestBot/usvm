package org.usvm.samples

import org.usvm.UMachineOptions
import org.usvm.machine.*
import org.usvm.language.*
import org.usvm.language.types.PythonType
import org.usvm.language.types.pythonInt
import org.usvm.machine.interpreters.CPythonExecutionException
import org.usvm.machine.interpreters.ConcretePythonInterpreter
import org.usvm.machine.interpreters.PythonObject
import org.usvm.test.util.TestRunner
import org.usvm.test.util.checkers.AnalysisResultsNumberMatcher
import java.io.File

open class PythonTestRunner(
    sourcePath: String,
    allowPathDiversions: Boolean = false
): TestRunner<PythonTest, PythonUnpinnedCallable, PythonType, PythonCoverage>() {
    override var options: UMachineOptions = UMachineOptions()
    private val testSources = File(PythonTestRunner::class.java.getResource(sourcePath)!!.file).readText()
    private val machine = PythonMachine(PythonProgram(testSources), allowPathDiversion = allowPathDiversions) { pythonObject ->
        val typeName = ConcretePythonInterpreter.getPythonObjectTypeName(pythonObject)
        PythonObjectInfo(
            ConcretePythonInterpreter.getPythonObjectRepr(pythonObject),
            typeName,
            if (typeName == "type") ConcretePythonInterpreter.getNameOfPythonType(pythonObject) else null
        )
    }
    override val typeTransformer: (Any?) -> PythonType
        get() = { _ -> pythonInt }
    override val checkType: (PythonType, PythonType) -> Boolean
        get() = { _, _ -> true }
    override val runner: (PythonUnpinnedCallable, UMachineOptions) -> List<PythonTest>
        get() = { callable, options ->
            val results: MutableList<PythonTest> = mutableListOf()
            machine.analyze(callable, results, options.stepLimit?.toInt() ?: 300)
            results
        }
    override val coverageRunner: (List<PythonTest>) -> PythonCoverage
        get() = { _ -> PythonCoverage(0) }

    private fun compareWithConcreteRun(
        target: PythonUnpinnedCallable,
        test: PythonTest,
        check: (PythonObject) -> String?
    ): String? {
        val namespace = ConcretePythonInterpreter.getNewNamespace()
        ConcretePythonInterpreter.concreteRun(namespace, testSources)
        val functionRef = target.reference(namespace)
        val converter = test.inputValueConverter
        converter.restart()
        val args = test.inputValues.map { converter.convert(it.asUExpr) }
        return try {
            val concreteResult = ConcretePythonInterpreter.concreteRunOnFunctionRef(functionRef, args)
            check(concreteResult)
        } catch (exception: CPythonExecutionException) {
            require(exception.pythonExceptionType != null)
            check(exception.pythonExceptionType!!)
        }
    }

    private inline fun <reified FUNCTION_TYPE : Function<Boolean>> createCheckWithConcreteRun(concreteRun: Boolean = true):
                (PythonUnpinnedCallable, AnalysisResultsNumberMatcher, (PythonTest, PythonObject) -> String?, List<FUNCTION_TYPE>, List<FUNCTION_TYPE>) -> Unit =
        { target: PythonUnpinnedCallable,
          analysisResultsNumberMatcher: AnalysisResultsNumberMatcher,
          compareConcolicAndConcrete: (PythonTest, PythonObject) -> String?,
          invariants: List<FUNCTION_TYPE>,
          propertiesToDiscover: List<FUNCTION_TYPE> ->
            val onAnalysisResult = { pythonTest: PythonTest ->
                val executionResult = when (val result = pythonTest.result) {
                    is Success -> result.output
                    is Fail -> result.exception
                }
                val result = pythonTest.inputValues.map { it.reprFromPythonObject } + executionResult
                if (concreteRun) {
                    val comparisonResult = compareWithConcreteRun(target, pythonTest) {
                        compareConcolicAndConcrete(pythonTest, it)
                    }
                    require(comparisonResult == null) {
                        "Error in CPython patch: concrete and concolic results differ. " +
                                "Checker msg: $comparisonResult. " +
                                "Inputs: ${pythonTest.inputValues.map { it.reprFromPythonObject }.joinToString(", ")}"
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
                { _, _ -> null },
                invariants,
                propertiesToDiscover
            )
        }

    protected val check1 = createCheck<(PythonObjectInfo, PythonObjectInfo) -> Boolean>()
    protected val check1WithConcreteRun =
        createCheckWithConcreteRun<(PythonObjectInfo, PythonObjectInfo) -> Boolean>()

    protected val check2 = createCheck<(PythonObjectInfo, PythonObjectInfo, PythonObjectInfo) -> Boolean>()
    protected val check2WithConcreteRun =
        createCheckWithConcreteRun<(PythonObjectInfo, PythonObjectInfo, PythonObjectInfo) -> Boolean>()

    protected val check3WithConcreteRun =
        createCheckWithConcreteRun<(PythonObjectInfo, PythonObjectInfo, PythonObjectInfo, PythonObjectInfo) -> Boolean>()

    protected val compareConcolicAndConcreteReprsIfSuccess:
                (PythonTest, PythonObject) -> String? = { testFromConcolic, concreteResult ->
        (testFromConcolic.result as? Success)?.let {
            val concolic = it.output.repr
            val concrete = ConcretePythonInterpreter.getPythonObjectRepr(concreteResult)
            if (concolic == concrete) null else "(Success) Expected $concrete, got $concolic"
        }
    }

    protected val compareConcolicAndConcreteTypesIfFail:
                (PythonTest, PythonObject) -> String? = { testFromConcolic, concreteResult ->
        (testFromConcolic.result as? Fail)?.let {
            if (ConcretePythonInterpreter.getPythonObjectTypeName(concreteResult) != "type")
                "Fail in concolic (${it.exception.selfTypeName}), but success in concrete (${ConcretePythonInterpreter.getPythonObjectRepr(concreteResult)})"
            else {
                val concolic = it.exception.selfTypeName
                val concrete = ConcretePythonInterpreter.getNameOfPythonType(concreteResult)
                if (concolic == concrete) null else "(Fail) Expected $concrete, got $concolic"
            }
        }
    }

    protected val standardConcolicAndConcreteChecks:
                (PythonTest, PythonObject) -> String? = { testFromConcolic, concreteResult ->
        compareConcolicAndConcreteReprsIfSuccess(testFromConcolic, concreteResult) ?:
                compareConcolicAndConcreteTypesIfFail(testFromConcolic, concreteResult)
    }

    protected fun constructFunction(name: String, signature: List<PythonType>): PythonUnpinnedCallable =
        PythonUnpinnedCallable.constructCallableFromName(signature, name)
}

class PythonObjectInfo(
    val repr: String,
    val typeName: String,
    val selfTypeName: String?
) {
    override fun toString(): String = "$repr: $typeName"
}

typealias PythonTest = PythonAnalysisResult<PythonObjectInfo>

data class PythonCoverage(val int: Int)