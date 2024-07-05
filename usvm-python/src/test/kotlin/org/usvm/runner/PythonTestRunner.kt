package org.usvm.runner

import org.usvm.UMachineOptions
import org.usvm.language.PyProgram
import org.usvm.language.PyUnpinnedCallable
import org.usvm.machine.CPythonExecutionException
import org.usvm.machine.PyMachine
import org.usvm.machine.interpreters.concrete.ConcretePythonInterpreter
import org.usvm.machine.interpreters.concrete.PyObject
import org.usvm.machine.results.DefaultPyMachineResultsReceiver
import org.usvm.machine.results.serialization.PythonObjectInfo
import org.usvm.machine.results.serialization.StandardPythonObjectSerializer
import org.usvm.machine.symbolicobjects.rendering.PyValueRenderer
import org.usvm.machine.types.BasicPythonTypeSystem
import org.usvm.machine.types.PythonAnyType
import org.usvm.machine.types.PythonType
import org.usvm.machine.types.PythonTypeSystem
import org.usvm.machine.types.PythonTypeSystemWithMypyInfo
import org.usvm.python.model.PyResultFailure
import org.usvm.python.model.PyResultSuccess
import org.usvm.python.model.PyTest
import org.usvm.test.util.TestRunner
import org.usvm.test.util.checkers.AnalysisResultsNumberMatcher
import org.usvm.test.util.checkers.ge

sealed class PythonTestRunner(
    override var options: UMachineOptions = UMachineOptions(),
    protected var allowPathDiversions: Boolean = false,
) : TestRunner<PyTest<PythonObjectInfo>, PyUnpinnedCallable, PythonType, PythonCoverage>() {
    var timeoutPerRunMs: Long? = null
    abstract val typeSystem: PythonTypeSystem
    protected abstract val program: PyProgram
    private val machine by lazy {
        PyMachine(program, typeSystem)
    }
    override val typeTransformer: (Any?) -> PythonType
        get() = { _ -> PythonAnyType }
    override val checkType: (PythonType, PythonType) -> Boolean
        get() = { _, _ -> true }
    override val runner: (PyUnpinnedCallable, UMachineOptions) -> List<PyTest<PythonObjectInfo>>
        get() = { callable, options ->
            val saver = DefaultPyMachineResultsReceiver(StandardPythonObjectSerializer)
            machine.analyze(
                callable,
                saver,
                options.stepLimit?.toInt() ?: 300,
                allowPathDiversion = allowPathDiversions,
                timeoutMs = options.timeout.inWholeMilliseconds,
                timeoutPerRunMs = timeoutPerRunMs,
            )
            saver.pyTestObserver.tests
        }
    override val coverageRunner: (List<PyTest<PythonObjectInfo>>) -> PythonCoverage
        get() = { _ -> PythonCoverage(0) }

    private fun compareWithConcreteRun(
        target: PyUnpinnedCallable,
        test: PyTest<PythonObjectInfo>,
        check: (PyObject) -> String?,
    ): String? =
        program.withPinnedCallable(target, typeSystem) { pinnedCallable ->
            val argModels = test.inputModel.inputArgs
            val renderer = PyValueRenderer()
            val args = argModels.map { renderer.convert(it) }
            try {
                val concreteResult =
                    ConcretePythonInterpreter.concreteRunOnFunctionRef(pinnedCallable.pyObject, args)
                check(concreteResult)
            } catch (exception: CPythonExecutionException) {
                requireNotNull(exception.pythonExceptionType)
                check(exception.pythonExceptionType!!)
            }
        }

    private inline fun <reified FUNCTION_TYPE : Function<Boolean>> createCheckWithConcreteRun(
        concreteRun: Boolean = true,
    ): (
        PyUnpinnedCallable,
        AnalysisResultsNumberMatcher,
        (PyTest<PythonObjectInfo>, PyObject) -> String?,
        List<FUNCTION_TYPE>,
        List<FUNCTION_TYPE>,
    ) -> Unit =
        { target: PyUnpinnedCallable,
                analysisResultsNumberMatcher: AnalysisResultsNumberMatcher,
                compareConcolicAndConcrete: (PyTest<PythonObjectInfo>, PyObject) -> String?,
                invariants: List<FUNCTION_TYPE>,
                propertiesToDiscover: List<FUNCTION_TYPE>,
            ->
            val onAnalysisResult = { pythonTest: PyTest<PythonObjectInfo> ->
                val executionResult = when (val result = pythonTest.result) {
                    is PyResultSuccess -> result.output
                    is PyResultFailure -> result.exception
                }
                val result = pythonTest.inputArgs + executionResult
                if (concreteRun) {
                    val comparisonResult = compareWithConcreteRun(target, pythonTest) {
                        compareConcolicAndConcrete(pythonTest, it)
                    }
                    require(comparisonResult == null) {
                        "Error in CPython patch or approximation: concrete and concolic results differ. " +
                            "Checker msg: $comparisonResult. " +
                            "Inputs: ${pythonTest.inputArgs.joinToString(", ")}"
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
                (target.signature.map { PythonAnyType } + PythonAnyType).toTypedArray(),
                CheckMode.MATCH_PROPERTIES,
                coverageChecker = { true }
            )
        }

    private inline fun <reified FUNCTION_TYPE : Function<Boolean>> createCheckWithConcreteRunAndNoPredicates():
        (PyUnpinnedCallable, (PyTest<PythonObjectInfo>, PyObject) -> String?) -> Unit =
        { target: PyUnpinnedCallable,
                compareConcolicAndConcrete: (PyTest<PythonObjectInfo>, PyObject) -> String?,
            ->
            createCheckWithConcreteRun<FUNCTION_TYPE>(concreteRun = true)(
                target,
                ge(0),
                compareConcolicAndConcrete,
                emptyList(),
                emptyList()
            )
        }

    private inline fun <reified FUNCTION_TYPE : Function<Boolean>> createCheck():
        (PyUnpinnedCallable, AnalysisResultsNumberMatcher, List<FUNCTION_TYPE>, List<FUNCTION_TYPE>) -> Unit =
        { target: PyUnpinnedCallable,
                analysisResultsNumberMatcher: AnalysisResultsNumberMatcher,
                invariants: List<FUNCTION_TYPE>,
                propertiesToDiscover: List<FUNCTION_TYPE>,
            ->
            createCheckWithConcreteRun<FUNCTION_TYPE>(concreteRun = false)(
                target,
                analysisResultsNumberMatcher,
                { _, _ -> null },
                invariants,
                propertiesToDiscover
            )
        }

    val check0 = createCheck<(PythonObjectInfo) -> Boolean>()
    val check0WithConcreteRun = createCheckWithConcreteRun<(PythonObjectInfo) -> Boolean>()
    val check0NoPredicates = createCheckWithConcreteRunAndNoPredicates<(PythonObjectInfo) -> Boolean>()

    val check1 = createCheck<(PythonObjectInfo, PythonObjectInfo) -> Boolean>()
    val check1WithConcreteRun = createCheckWithConcreteRun<(PythonObjectInfo, PythonObjectInfo) -> Boolean>()
    val check1NoPredicates =
        createCheckWithConcreteRunAndNoPredicates<(PythonObjectInfo, PythonObjectInfo) -> Boolean>()

    val check2 = createCheck<(PythonObjectInfo, PythonObjectInfo, PythonObjectInfo) -> Boolean>()
    val check2WithConcreteRun =
        createCheckWithConcreteRun<(PythonObjectInfo, PythonObjectInfo, PythonObjectInfo) -> Boolean>()
    val check2NoPredicates =
        createCheckWithConcreteRunAndNoPredicates<(PythonObjectInfo, PythonObjectInfo, PythonObjectInfo) -> Boolean>()

    val check3WithConcreteRun =
        createCheckWithConcreteRun<
            (
                PythonObjectInfo,
                PythonObjectInfo,
                PythonObjectInfo,
                PythonObjectInfo,
            ) -> Boolean
            >()
    val check3NoPredicates =
        createCheckWithConcreteRunAndNoPredicates<
            (
                PythonObjectInfo,
                PythonObjectInfo,
                PythonObjectInfo,
                PythonObjectInfo,
            ) -> Boolean
            >()

    val check4NoPredicates =
        createCheckWithConcreteRunAndNoPredicates<
            (
                PythonObjectInfo,
                PythonObjectInfo,
                PythonObjectInfo,
                PythonObjectInfo,
                PythonObjectInfo,
            ) -> Boolean
            >()

    protected val compareConcolicAndConcreteReprsIfSuccess:
        (PyTest<PythonObjectInfo>, PyObject) -> String? = { testFromConcolic, concreteResult ->
            (testFromConcolic.result as? PyResultSuccess)?.let {
                val concolic = it.output.repr
                val concrete = ConcretePythonInterpreter.getPythonObjectRepr(concreteResult)
                if (concolic == concrete) null else "(Success) Expected $concrete, got $concolic"
            }
        }

    protected val compareConcolicAndConcreteTypesIfSuccess:
        (PyTest<PythonObjectInfo>, PyObject) -> String? = { testFromConcolic, concreteResult ->
            (testFromConcolic.result as? PyResultSuccess)?.let {
                val concolic = it.output.typeName
                val concrete = ConcretePythonInterpreter.getPythonObjectTypeName(concreteResult)
                if (concolic == concrete) null else "(Success) Expected result type $concrete, got $concolic"
            }
        }

    protected val compareConcolicAndConcreteTypesIfFail:
        (PyTest<PythonObjectInfo>, PyObject) -> String? = { testFromConcolic, concreteResult ->
            (testFromConcolic.result as? PyResultFailure)?.let {
                if (ConcretePythonInterpreter.getPythonObjectTypeName(concreteResult) != "type") {
                    "Fail in concolic (${it.exception.selfTypeName}), " +
                        "but success in concrete (${ConcretePythonInterpreter.getPythonObjectRepr(concreteResult)})"
                } else {
                    val concolic = it.exception.selfTypeName
                    val concrete = ConcretePythonInterpreter.getNameOfPythonType(concreteResult)
                    if (concolic == concrete) null else "(Fail) Expected $concrete, got $concolic"
                }
            }
        }

    val standardConcolicAndConcreteChecks:
        (PyTest<PythonObjectInfo>, PyObject) -> String? = { testFromConcolic, concreteResult ->
            compareConcolicAndConcreteReprsIfSuccess(testFromConcolic, concreteResult)
                ?: compareConcolicAndConcreteTypesIfFail(testFromConcolic, concreteResult)
        }

    val compareConcolicAndConcreteTypes:
        (PyTest<PythonObjectInfo>, PyObject) -> String? = { testFromConcolic, concreteResult ->
            compareConcolicAndConcreteTypesIfSuccess(testFromConcolic, concreteResult)
                ?: compareConcolicAndConcreteTypesIfFail(testFromConcolic, concreteResult)
        }

    protected open fun constructFunction(name: String, signature: List<PythonType>): PyUnpinnedCallable =
        PyUnpinnedCallable.constructCallableFromName(signature, name)
}

open class PythonTestRunnerForPrimitiveProgram(
    module: String,
    options: UMachineOptions = UMachineOptions(),
    allowPathDiversions: Boolean = false,
) : PythonTestRunner(options, allowPathDiversions) {
    override val program = SamplesBuild.program.getPrimitiveProgram(module)
    override val typeSystem = BasicPythonTypeSystem()
}

open class PythonTestRunnerForStructuredProgram(
    private val module: String,
    options: UMachineOptions = UMachineOptions(),
    allowPathDiversions: Boolean = false,
) : PythonTestRunner(options, allowPathDiversions) {
    override val program = SamplesBuild.program
    override val typeSystem = PythonTypeSystemWithMypyInfo(SamplesBuild.mypyBuild, SamplesBuild.program)
    override fun constructFunction(name: String, signature: List<PythonType>): PyUnpinnedCallable =
        PyUnpinnedCallable.constructCallableFromName(signature, name, module)
}

class CustomPythonTestRunner(
    override val program: PyProgram,
    override val typeSystem: PythonTypeSystem,
    options: UMachineOptions = UMachineOptions(),
    allowPathDiversions: Boolean = false,
) : PythonTestRunner(options, allowPathDiversions)

data class PythonCoverage(val int: Int)
