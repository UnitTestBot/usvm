package org.usvm.runner

import org.usvm.UMachineOptions
import org.usvm.machine.*
import org.usvm.language.*
import org.usvm.language.types.*
import org.usvm.machine.interpreters.CPythonExecutionException
import org.usvm.machine.interpreters.ConcretePythonInterpreter
import org.usvm.machine.interpreters.PythonObject
import org.usvm.test.util.TestRunner
import org.usvm.test.util.checkers.AnalysisResultsNumberMatcher

sealed class PythonTestRunner(
    protected val module: String,
    override var options: UMachineOptions = UMachineOptions(),
    protected var allowPathDiversions: Boolean = false
): TestRunner<PythonTest, PythonUnpinnedCallable, PythonType, PythonCoverage>() {
    protected abstract val typeSystem: PythonTypeSystem
    protected abstract val program: PythonProgram
    private val machine by lazy {
        PythonMachine(program, typeSystem) { pythonObject ->
            val typeName = ConcretePythonInterpreter.getPythonObjectTypeName(pythonObject)
            PythonObjectInfo(
                ConcretePythonInterpreter.getPythonObjectRepr(pythonObject),
                typeName,
                if (typeName == "type") ConcretePythonInterpreter.getNameOfPythonType(pythonObject) else null
            )
        }
    }
    override val typeTransformer: (Any?) -> PythonType
        get() = { _ -> pythonInt }
    override val checkType: (PythonType, PythonType) -> Boolean
        get() = { _, _ -> true }
    override val runner: (PythonUnpinnedCallable, UMachineOptions) -> List<PythonTest>
        get() = { callable, options ->
            val results: MutableList<PythonTest> = mutableListOf()
            machine.analyze(
                callable,
                results,
                options.stepLimit?.toInt() ?: 300,
                allowPathDiversion = allowPathDiversions
            )
            results
        }
    override val coverageRunner: (List<PythonTest>) -> PythonCoverage
        get() = { _ -> PythonCoverage(0) }

    private fun compareWithConcreteRun(
        target: PythonUnpinnedCallable,
        test: PythonTest,
        check: (PythonObject) -> String?
    ): String? =
        program.withPinnedCallable(target) { pinnedCallable ->
            val converter = test.inputValueConverter
            converter.restart()
            val args = test.inputValues.map { converter.convert(it.asUExpr) }
            try {
                val concreteResult =
                    ConcretePythonInterpreter.concreteRunOnFunctionRef(pinnedCallable.asPythonObject, args)
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

    protected val compareConcolicAndConcreteTypesIfSuccess:
                (PythonTest, PythonObject) -> String? = { testFromConcolic, concreteResult ->
        (testFromConcolic.result as? Success)?.let {
            val concolic = it.output.typeName
            val concrete = ConcretePythonInterpreter.getPythonObjectTypeName(concreteResult)
            if (concolic == concrete) null else "(Success) Expected result type $concrete, got $concolic"
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

    protected val compareConcolicAndConcreteTypes:
                (PythonTest, PythonObject) -> String? = { testFromConcolic, concreteResult ->
        compareConcolicAndConcreteTypesIfSuccess(testFromConcolic, concreteResult) ?:
        compareConcolicAndConcreteTypesIfFail(testFromConcolic, concreteResult)
    }

    protected open fun constructFunction(name: String, signature: List<PythonType>): PythonUnpinnedCallable =
        PythonUnpinnedCallable.constructCallableFromName(signature, name)
}

open class PythonTestRunnerForPrimitiveProgram(
    module: String,
    options: UMachineOptions = UMachineOptions(),
    allowPathDiversions: Boolean = false
): PythonTestRunner(module, options, allowPathDiversions) {
    override val program = SamplesBuild.program.getPrimitiveProgram(module)
    override val typeSystem = BasicPythonTypeSystem()
}

open class PythonTestRunnerForStructuredProgram(
    module: String,
    options: UMachineOptions = UMachineOptions(),
    allowPathDiversions: Boolean = false
): PythonTestRunner(module, options, allowPathDiversions) {
    override val program = SamplesBuild.program
    override val typeSystem = PythonTypeSystemWithMypyInfo(SamplesBuild.mypyBuild, program)
    override fun constructFunction(name: String, signature: List<PythonType>): PythonUnpinnedCallable =
        PythonUnpinnedCallable.constructCallableFromName(signature, name, module)
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