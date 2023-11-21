package org.usvm.runner

import org.usvm.language.PythonUnpinnedCallable
import org.usvm.language.StructuredPythonProgram
import org.usvm.language.types.PythonTypeSystemWithMypyInfo
import org.usvm.language.types.getTypeFromTypeHint
import org.usvm.machine.PythonMachine
import org.usvm.machine.saving.PickledObjectSaver
import org.utbot.python.newtyping.PythonCallableTypeDescription
import org.utbot.python.newtyping.PythonCompositeTypeDescription
import org.utbot.python.newtyping.general.FunctionType
import org.utbot.python.newtyping.general.UtType
import org.utbot.python.newtyping.mypy.MypyBuildDirectory
import org.utbot.python.newtyping.mypy.readMypyInfoBuild
import org.utbot.python.newtyping.pythonDescription
import java.io.File

class PythonMachineSocketRunner(
    mypyDirPath: File,
    programRoots: Set<File>,
    socketIp: String,
    socketPort: Int
): AutoCloseable {
    private val mypyDir = MypyBuildDirectory(mypyDirPath, programRoots.map { it.canonicalPath }.toSet())
    private val mypyBuild = readMypyInfoBuild(mypyDir)
    private val communicator = PickledObjectCommunicator(socketIp, socketPort)
    private val program = StructuredPythonProgram(programRoots)
    private val typeSystem = PythonTypeSystemWithMypyInfo(mypyBuild, program)
    private val machine = PythonMachine(program, typeSystem)
    override fun close() {
        communicator.close()
        machine.close()
    }

    private fun validateFunctionType(type: UtType): FunctionType {
        val description = type.pythonDescription() as? PythonCallableTypeDescription
            ?: error("Specified definition is not a function")
        if (description.argumentKinds.any { it == PythonCallableTypeDescription.ArgKind.ARG_STAR || it == PythonCallableTypeDescription.ArgKind.ARG_STAR_2 })
            error("Named arguments are not supported in symbolic execution")
        return type as FunctionType
    }

    private fun analyze(
        callable: PythonUnpinnedCallable,
        timeoutPerRunMs: Long,
        timeoutMs: Long
    ) {
        machine.analyze(
            callable,
            PickledObjectSaver(communicator),
            timeoutMs = timeoutMs,
            timeoutPerRunMs = timeoutPerRunMs,
            maxIterations = 1000
        )
    }

    fun analyzeFunction(
        module: String,
        functionName: String,
        timeoutPerRunMs: Long,
        timeoutMs: Long
    ) {
        val def = mypyBuild.definitions[module]?.let { it[functionName] }
            ?: error("Did not find specified function in mypy build")
        val type = def.getUtBotType()
        val callableType = validateFunctionType(type)
        val unpinnedCallable = PythonUnpinnedCallable.constructCallableFromName(
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
        timeoutMs: Long
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
        val unpinnedCallable = PythonUnpinnedCallable.constructCallableFromName(
            callableType.arguments.map {
                getTypeFromTypeHint(it, typeSystem)
            },
            "$clsName.$functionName",
            module
        )
        analyze(unpinnedCallable, timeoutPerRunMs, timeoutMs)
    }
}