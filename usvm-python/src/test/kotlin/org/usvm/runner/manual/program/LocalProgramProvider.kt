package org.usvm.runner.manual.program

import org.usvm.language.PyProgram
import org.usvm.language.PyUnpinnedCallable
import org.usvm.language.StructuredPyProgram
import org.usvm.machine.interpreters.concrete.ConcretePythonInterpreter
import org.usvm.machine.types.BasicPythonTypeSystem
import org.usvm.machine.types.PythonTypeSystem
import org.usvm.machine.types.PythonTypeSystemWithMypyInfo
import org.usvm.machine.types.getTypeFromTypeHint
import org.usvm.machine.utils.withAdditionalPaths
import org.utpython.types.PythonCallableTypeDescription
import org.utpython.types.general.FunctionType
import org.utpython.types.general.UtType
import org.utpython.types.pythonDescription
import org.utpython.types.pythonTypeRepresentation

class LocalProgramProvider(
    val path: String,
    private val ignoreFunctions: List<String> = emptyList(),
    private val ignoreModules: List<String> = emptyList(),
) : ProgramProvider {
    override val program: PyProgram
        get() = TODO("Not yet implemented")

    override val typeSystem: PythonTypeSystem

    override val functions: List<PyUnpinnedCallable>
        get() = TODO("Not yet implemented")

    init {
        typeSystem = BasicPythonTypeSystem() // TODO
    }

    private fun getFunctionInfo(
        type: UtType,
        name: String,
        module: String,
        typeSystem: PythonTypeSystemWithMypyInfo,
        program: StructuredPyProgram,
    ): PyUnpinnedCallable? {
        val description = type.pythonDescription()
        if (description !is PythonCallableTypeDescription) {
            return null
        }
        if (ignoreFunctions.contains(name)) {
            return null
        }
        if (description.argumentKinds.any {
                it == PythonCallableTypeDescription.ArgKind.ARG_STAR || it == PythonCallableTypeDescription.ArgKind.ARG_STAR_2
            }) {
            return null
        }
        runCatching {
            withAdditionalPaths(program.roots, typeSystem) {
                val namespace = program.getNamespaceOfModule(module)!!
                val func = ConcretePythonInterpreter.eval(namespace, name)
                if (ConcretePythonInterpreter.getPythonObjectTypeName(func) != "function") {
                    null
                } else {
                    func
                }
            }
        }.getOrNull() ?: return null
        println("$module.$name: ${type.pythonTypeRepresentation()}")
        val callableType = type as FunctionType
        return PyUnpinnedCallable.constructCallableFromName(
            callableType.arguments.map {
                getTypeFromTypeHint(it, typeSystem)
            },
            name,
            module
        )
    }
}