package org.usvm.runner

import org.usvm.UMachineOptions
import org.usvm.machine.*
import org.usvm.language.*
import org.usvm.language.types.*
import org.usvm.machine.interpreters.concrete.CPythonExecutionException
import org.usvm.machine.interpreters.concrete.ConcretePythonInterpreter
import org.usvm.machine.interpreters.concrete.PythonObject
import org.usvm.machine.rendering.PythonObjectRenderer
import org.usvm.machine.results.*
import org.usvm.python.model.PyResultFailure
import org.usvm.python.model.PyResultSuccess
import org.usvm.python.model.PyTest
import org.usvm.test.util.TestRunner
import org.usvm.test.util.checkers.AnalysisResultsNumberMatcher
import org.usvm.test.util.checkers.ge

sealed class PythonTestRunner(
    override var options: UMachineOptions = UMachineOptions(),
    protected var allowPathDiversions: Boolean = false
): TestRunner<PyTest<PythonObjectInfo>, PythonUnpinnedCallable, PythonType, PythonCoverage>() {
    var timeoutPerRunMs: Long? = null
    abstract val typeSystem: PythonTypeSystem
    protected abstract val program: PythonProgram
    private val machine by lazy {
        PyMachine(program, typeSystem)
    }
    override val typeTransformer: (Any?) -> PythonType
        get() = { _ -> PythonAnyType }
    override val checkType: (PythonType, PythonType) -> Boolean
        get() = { _, _ -> true }
    override val runner: (PythonUnpinnedCallable, UMachineOptions) -> List<PyTest<PythonObjectInfo>>
        get() = { callable, options ->
            val saver = DefaultPyMachineResultsReceiver(StandardPythonObjectSerializer)
            machine.analyze(
                callable,
                saver,
                options.stepLimit?.toInt() ?: 300,
                allowPathDiversion = allowPathDiversions,
                timeoutMs = options.timeout.inWholeMilliseconds,
                timeoutPerRunMs = timeoutPerRunMs
            )
            saver.pyTestObserver.tests
        }
    override val coverageRunner: (List<PyTest<PythonObjectInfo>>) -> PythonCoverage
        get() = { _ -> PythonCoverage(0) }

    private fun compareWithConcreteRun(
        target: PythonUnpinnedCallable,
        test: PyTest<PythonObjectInfo>,
        check: (PythonObject) -> String?
    ): String? =
        program.withPinnedCallable(target, typeSystem) { pinnedCallable ->
            val argModels = test.inputModel.inputArgs
            val renderer = PythonObjectRenderer()
            val args = argModels.map { renderer.convert(it) }
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
                (PythonUnpinnedCallable, AnalysisResultsNumberMatcher, (PyTest<PythonObjectInfo>, PythonObject) -> String?, List<FUNCTION_TYPE>, List<FUNCTION_TYPE>) -> Unit =
        { target: PythonUnpinnedCallable,
          analysisResultsNumberMatcher: AnalysisResultsNumberMatcher,
          compareConcolicAndConcrete: (PyTest<PythonObjectInfo>, PythonObject) -> String?,
          invariants: List<FUNCTION_TYPE>,
          propertiesToDiscover: List<FUNCTION_TYPE> ->
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
                (PythonUnpinnedCallable, (PyTest<PythonObjectInfo>, PythonObject) -> String?) -> Unit =
        { target: PythonUnpinnedCallable,
          compareConcolicAndConcrete: (PyTest<PythonObjectInfo>, PythonObject) -> String? ->
            createCheckWithConcreteRun<FUNCTION_TYPE>(concreteRun = true)(
                target,
                ge(0),
                compareConcolicAndConcrete,
                emptyList(),
                emptyList()
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
        createCheckWithConcreteRun<(PythonObjectInfo, PythonObjectInfo, PythonObjectInfo, PythonObjectInfo) -> Boolean>()
    val check3NoPredicates =
        createCheckWithConcreteRunAndNoPredicates<(PythonObjectInfo, PythonObjectInfo, PythonObjectInfo, PythonObjectInfo) -> Boolean>()

    val check4NoPredicates =
        createCheckWithConcreteRunAndNoPredicates<(PythonObjectInfo, PythonObjectInfo, PythonObjectInfo, PythonObjectInfo, PythonObjectInfo) -> Boolean>()

    protected val compareConcolicAndConcreteReprsIfSuccess:
                (PyTest<PythonObjectInfo>, PythonObject) -> String? = { testFromConcolic, concreteResult ->
        (testFromConcolic.result as? PyResultSuccess)?.let {
            val concolic = it.output.repr
            val concrete = ConcretePythonInterpreter.getPythonObjectRepr(concreteResult)
            if (concolic == concrete) null else "(Success) Expected $concrete, got $concolic"
        }
    }

    protected val compareConcolicAndConcreteTypesIfSuccess:
                (PyTest<PythonObjectInfo>, PythonObject) -> String? = { testFromConcolic, concreteResult ->
        (testFromConcolic.result as? PyResultSuccess)?.let {
            val concolic = it.output.typeName
            val concrete = ConcretePythonInterpreter.getPythonObjectTypeName(concreteResult)
            if (concolic == concrete) null else "(Success) Expected result type $concrete, got $concolic"
        }
    }

    protected val compareConcolicAndConcreteTypesIfFail:
                (PyTest<PythonObjectInfo>, PythonObject) -> String? = { testFromConcolic, concreteResult ->
        (testFromConcolic.result as? PyResultFailure)?.let {
            if (ConcretePythonInterpreter.getPythonObjectTypeName(concreteResult) != "type")
                "Fail in concolic (${it.exception.selfTypeName}), but success in concrete (${ConcretePythonInterpreter.getPythonObjectRepr(concreteResult)})"
            else {
                val concolic = it.exception.selfTypeName
                val concrete = ConcretePythonInterpreter.getNameOfPythonType(concreteResult)
                if (concolic == concrete) null else "(Fail) Expected $concrete, got $concolic"
            }
        }
    }

    val standardConcolicAndConcreteChecks:
                (PyTest<PythonObjectInfo>, PythonObject) -> String? = { testFromConcolic, concreteResult ->
        compareConcolicAndConcreteReprsIfSuccess(testFromConcolic, concreteResult) ?:
                compareConcolicAndConcreteTypesIfFail(testFromConcolic, concreteResult)
    }

    val compareConcolicAndConcreteTypes:
                (PyTest<PythonObjectInfo>, PythonObject) -> String? = { testFromConcolic, concreteResult ->
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
): PythonTestRunner(options, allowPathDiversions) {
    override val program = SamplesBuild.program.getPrimitiveProgram(module)
    override val typeSystem = BasicPythonTypeSystem()
}

open class PythonTestRunnerForStructuredProgram(
    private val module: String,
    options: UMachineOptions = UMachineOptions(),
    allowPathDiversions: Boolean = false
): PythonTestRunner(options, allowPathDiversions) {
    override val program = SamplesBuild.program
    override val typeSystem = PythonTypeSystemWithMypyInfo(SamplesBuild.mypyBuild, SamplesBuild.program)
    override fun constructFunction(name: String, signature: List<PythonType>): PythonUnpinnedCallable =
        PythonUnpinnedCallable.constructCallableFromName(signature, name, module)
}

class CustomPythonTestRunner(
    override val program: PythonProgram,
    override val typeSystem: PythonTypeSystem,
    options: UMachineOptions = UMachineOptions(),
    allowPathDiversions: Boolean = false
): PythonTestRunner(options, allowPathDiversions)

data class PythonCoverage(val int: Int)