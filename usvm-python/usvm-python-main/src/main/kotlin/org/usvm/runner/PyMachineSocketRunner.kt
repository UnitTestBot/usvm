package org.usvm.runner

import org.usvm.language.PyUnpinnedCallable
import org.usvm.language.StructuredPyProgram
import org.usvm.machine.PyMachine
import org.usvm.machine.results.PyMachineResultsReceiver
import org.usvm.machine.results.observers.EmptyInputPythonObjectObserver
import org.usvm.machine.results.observers.EmptyNewStateObserver
import org.usvm.machine.results.observers.EmptyPyTestObserver
import org.usvm.machine.results.observers.NewStateObserver
import org.usvm.machine.results.observers.PyTestObserver
import org.usvm.machine.results.serialization.EmptyObjectSerializer
import org.usvm.machine.types.PythonTypeSystemWithMypyInfo
import org.usvm.machine.types.getTypeFromTypeHint
import org.usvm.python.ps.PyPathSelectorType
import org.utpython.types.PythonCallableTypeDescription
import org.utpython.types.PythonCompositeTypeDescription
import org.utpython.types.general.FunctionType
import org.utpython.types.general.UtType
import org.utpython.types.mypy.MypyBuildDirectory
import org.utpython.types.mypy.readMypyInfoBuild
import org.utpython.types.pythonDescription
import java.io.File

class PyMachineSocketRunner(
    mypyDirPath: File,
    programRoots: Set<File>,
    socketIp: String,
    socketPort: Int,
    pathSelector: PyPathSelectorType,
) : AutoCloseable {
    private val mypyDir = MypyBuildDirectory(mypyDirPath, programRoots.map { it.canonicalPath }.toSet())
    private val mypyBuild = readMypyInfoBuild(mypyDir)
    private val communicator = PickledObjectCommunicator(socketIp, socketPort)
    private val program = StructuredPyProgram(programRoots)
    private val typeSystem = PythonTypeSystemWithMypyInfo(mypyBuild, program)
    private val machine = PyMachine(program, typeSystem, pathSelectorType = pathSelector)
    override fun close() {
        communicator.close()
        machine.close()
    }

    private fun validateFunctionType(type: UtType): FunctionType {
        val description = type.pythonDescription() as? PythonCallableTypeDescription
            ?: error("Specified definition is not a function")
        if (description.argumentKinds.any {
                it == PythonCallableTypeDescription.ArgKind.ARG_STAR ||
                    it == PythonCallableTypeDescription.ArgKind.ARG_STAR_2
            }
        ) {
            error("Named arguments are not supported in symbolic execution")
        }
        return type as FunctionType
    }

    private fun analyze(
        callable: PyUnpinnedCallable,
        timeoutPerRunMs: Long,
        timeoutMs: Long,
    ) = machine.analyze(
        callable,
        saver = ResultReceiver(communicator),
        timeoutMs = timeoutMs,
        timeoutPerRunMs = timeoutPerRunMs,
        maxIterations = 1000,
    )

    fun analyzeFunction(
        module: String,
        functionName: String,
        timeoutPerRunMs: Long,
        timeoutMs: Long,
    ) {
        val def = mypyBuild.definitions[module]?.let { it[functionName] }
            ?: error("Did not find specified function in mypy build")
        val type = def.getUtBotType()
        val callableType = validateFunctionType(type)
        val unpinnedCallable = PyUnpinnedCallable.constructCallableFromName(
            callableType.arguments.map {
                getTypeFromTypeHint(it, typeSystem)
            },
            functionName,
            module
        )
        analyze(unpinnedCallable, timeoutPerRunMs, timeoutMs)
    }

    fun analyzeMethod(
        module: String,
        functionName: String,
        clsName: String,
        timeoutPerRunMs: Long,
        timeoutMs: Long,
    ) {
        val def = mypyBuild.definitions[module]?.let { it[clsName] }
            ?: error("Did not find specified class in mypy build")
        val clsType = def.getUtBotType()
        val clsDescr = clsType.pythonDescription() as? PythonCompositeTypeDescription
            ?: error("Specified class UtType description is not PythonCompositeTypeDescription")
        val funcDef = clsDescr.getMemberByName(typeSystem.typeHintsStorage, clsType, functionName)
            ?: error("Did not find method $functionName in $clsName members")
        val type = funcDef.type
        val callableType = validateFunctionType(type)
        val unpinnedCallable = PyUnpinnedCallable.constructCallableFromName(
            callableType.arguments.map {
                getTypeFromTypeHint(it, typeSystem)
            },
            "$clsName.$functionName",
            module
        )
        analyze(unpinnedCallable, timeoutPerRunMs, timeoutMs)
    }

    private class ResultReceiver(communicator: PickledObjectCommunicator) : PyMachineResultsReceiver<Unit> {
        override val newStateObserver: NewStateObserver = EmptyNewStateObserver
        override val serializer = EmptyObjectSerializer
        override val inputModelObserver = InputModelObserverForRunner(communicator)
        override val inputPythonObjectObserver = EmptyInputPythonObjectObserver
        override val pyTestObserver: PyTestObserver<Unit> = EmptyPyTestObserver()
    }
}
