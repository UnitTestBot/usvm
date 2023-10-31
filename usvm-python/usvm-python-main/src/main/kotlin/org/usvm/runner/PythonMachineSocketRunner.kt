package org.usvm.runner

import org.usvm.language.PythonUnpinnedCallable
import org.usvm.language.StructuredPythonProgram
import org.usvm.language.types.PythonTypeSystemWithMypyInfo
import org.usvm.language.types.SupportsTypeHint
import org.usvm.machine.PythonMachine
import org.usvm.machine.saving.PickledObjectSaver
import org.utbot.python.newtyping.PythonCallableTypeDescription
import org.utbot.python.newtyping.general.FunctionType
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

    fun analyzeFunction(
        module: String,
        functionName: String,
        timeoutPerRunMs: Long,
        timeoutMs: Long
    ) {
        val def = mypyBuild.definitions[module]?.let { it[functionName] }
            ?: error("Did not find specified function in mypy build")
        val type = def.getUtBotType()
        val description = type.pythonDescription() as? PythonCallableTypeDescription
            ?: error("Specified definition is not a function")
        if (description.argumentKinds.any { it == PythonCallableTypeDescription.ArgKind.ARG_STAR || it == PythonCallableTypeDescription.ArgKind.ARG_STAR_2 })
            error("Named arguments are not supported in symbolic execution")
        val callableType = type as FunctionType
        val unpinnedCallable = PythonUnpinnedCallable.constructCallableFromName(
            callableType.arguments.map {
                SupportsTypeHint(it, typeSystem)
            },
            functionName,
            module
        )
        machine.analyze(
            unpinnedCallable,
            PickledObjectSaver(communicator),
            timeoutMs = timeoutMs,
            timeoutPerRunMs = timeoutPerRunMs
        )
    }
}