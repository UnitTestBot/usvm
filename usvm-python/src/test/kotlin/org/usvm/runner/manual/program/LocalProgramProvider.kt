package org.usvm.runner.manual.program

import org.usvm.language.PyUnpinnedCallable
import org.usvm.language.StructuredPyProgram
import org.usvm.machine.interpreters.concrete.ConcretePythonInterpreter
import org.usvm.machine.types.PythonTypeSystem
import org.usvm.machine.types.PythonTypeSystemWithMypyInfo
import org.usvm.machine.types.getTypeFromTypeHint
import org.usvm.machine.utils.withAdditionalPaths
import org.usvm.runner.manual.manualTestLogger
import org.usvm.utils.getModulesFromFiles
import org.usvm.utils.getPythonFilesFromRoot
import org.utpython.types.PythonCallableTypeDescription
import org.utpython.types.PythonConcreteCompositeTypeDescription
import org.utpython.types.general.FunctionType
import org.utpython.types.general.UtType
import org.utpython.types.mypy.MypyBuildDirectory
import org.utpython.types.mypy.MypyInfoBuild
import org.utpython.types.mypy.buildMypyInfo
import org.utpython.types.mypy.readMypyInfoBuild
import org.utpython.types.pythonDescription
import org.utpython.types.pythonTypeRepresentation
import java.io.File
import kotlin.io.path.createTempDirectory

class LocalProgramProvider(
    projectPath: String,
    private val ignoreFunctions: List<String> = emptyList(),
    private val ignoreModules: List<String> = emptyList(),
) : ProgramProvider {
    override val program: StructuredPyProgram
    override val typeSystem: PythonTypeSystem
    override val functions: List<PyUnpinnedCallable>

    private val files = getPythonFilesFromRoot(projectPath).filter { !it.name.contains("__init__") }
    private val modules = getModulesFromFiles(projectPath, files).filter { it !in ignoreModules }
    private val pythonPath = System.getProperty("python.binary.path")
        ?: error("python.binary.path definition not found.")
    private val mypyBuild: MypyInfoBuild

    init {
        val mypyRoot = createTempDirectory(prefix = "mypy")
        try {
            val mypyDir = MypyBuildDirectory(mypyRoot.toFile(), setOf(projectPath))
            buildMypyInfo(
                pythonPath,
                files.map { it.canonicalPath },
                modules,
                mypyDir
            )
            mypyBuild = readMypyInfoBuild(mypyDir)
            program = StructuredPyProgram(setOf(File(projectPath)))
            typeSystem = PythonTypeSystemWithMypyInfo(mypyBuild, program)
            functions = modules.flatMap { module ->
                runCatching {
                    withAdditionalPaths(program.roots, typeSystem) {
                        program.getNamespaceOfModule(module)
                    }
                }.getOrNull() ?: return@flatMap emptyList() // skip bad modules
                val definition = mypyBuild.definitions[module] ?: error("$module must be in mypyBuild.definitions")
                definition.flatMap { (defName, def) ->
                    val type = def.getUtBotType()
                    val description = type.pythonDescription()
                    if (defName.startsWith("__")) {
                        emptyList()
                    } else if (description is PythonConcreteCompositeTypeDescription) {
                        extractFunctionFromClass(
                            module,
                            defName,
                            type,
                            description,
                            program,
                            typeSystem,
                            ignoreFunctions
                        )
                    } else {
                        getFunctionInfo(
                            type,
                            defName,
                            module,
                            typeSystem,
                            program,
                            ignoreFunctions
                        )?.let { listOf(it) }.orEmpty()
                    }
                }
            }
        } finally {
            mypyRoot.toFile().deleteRecursively()
        }
    }
}

private fun getFunctionInfo(
    type: UtType,
    name: String,
    module: String,
    typeSystem: PythonTypeSystemWithMypyInfo,
    program: StructuredPyProgram,
    ignoreFunctions: List<String>,
): PyUnpinnedCallable? {
    val description = type.pythonDescription()
    if (description !is PythonCallableTypeDescription) {
        return null
    }
    if (ignoreFunctions.contains(name)) {
        return null
    }
    if (description.argumentKinds.any {
            it == PythonCallableTypeDescription.ArgKind.ARG_STAR ||
                it == PythonCallableTypeDescription.ArgKind.ARG_STAR_2
        }
    ) {
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
    manualTestLogger.info("$module.$name: ${type.pythonTypeRepresentation()}")
    val callableType = type as FunctionType
    return PyUnpinnedCallable.constructCallableFromName(
        callableType.arguments.map {
            getTypeFromTypeHint(it, typeSystem)
        },
        name,
        module
    )
}

private fun extractFunctionFromClass(
    module: String,
    defName: String,
    type: UtType,
    description: PythonConcreteCompositeTypeDescription,
    program: StructuredPyProgram,
    typeSystem: PythonTypeSystemWithMypyInfo,
    ignoreFunctions: List<String>,
): List<PyUnpinnedCallable> {
    val members = description.getNamedMembers(type)
    return members.mapNotNull { memberDef ->
        if (memberDef.meta.name.startsWith("__")) {
            return@mapNotNull null
        }
        memberDef.type
        val name = "$defName.${memberDef.meta.name}"
        getFunctionInfo(memberDef.type, name, module, typeSystem, program, ignoreFunctions)
    }
}
