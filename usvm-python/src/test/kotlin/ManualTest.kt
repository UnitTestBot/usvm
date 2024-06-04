import org.usvm.UMachineOptions
import org.usvm.language.PyProgram
import org.usvm.language.PyUnpinnedCallable
import org.usvm.language.StructuredPyProgram
import org.usvm.machine.interpreters.concrete.ConcretePythonInterpreter
import org.usvm.machine.interpreters.concrete.IllegalOperationException
import org.usvm.machine.types.PythonTypeSystem
import org.usvm.machine.types.PythonTypeSystemWithMypyInfo
import org.usvm.machine.types.getTypeFromTypeHint
import org.usvm.machine.utils.withAdditionalPaths
import org.usvm.runner.CustomPythonTestRunner
import org.usvm.runner.manual.analyzers.OrdinaryAnalyzer
import org.usvm.runner.manual.program.sampleFunction
import org.usvm.utils.getModulesFromFiles
import org.usvm.utils.getPythonFilesFromRoot
import org.utpython.types.PythonCallableTypeDescription
import org.utpython.types.PythonConcreteCompositeTypeDescription
import org.utpython.types.general.FunctionType
import org.utpython.types.general.UtType
import org.utpython.types.mypy.MypyBuildDirectory
import org.utpython.types.mypy.buildMypyInfo
import org.utpython.types.mypy.readMypyInfoBuild
import org.utpython.types.pythonDescription
import org.utpython.types.pythonTypeRepresentation
import java.io.File
import kotlin.time.Duration.Companion.seconds

fun main() {
    /**
     * See:
     *  - [org.usvm.runner.manual.program.sampleStringFunction]
     *  - [org.usvm.runner.manual.program.sampleFunction]
     *  - [org.usvm.runner.manual.program.LocalProgramProvider]
     * */
    val program = sampleFunction

    /**
     * TODO
     * */
    val analyzer = OrdinaryAnalyzer

    analyzer.run(program)
}

private val ignoreFunctions = listOf<String>()
private val ignoreModules = listOf<String>(
    "odd_even_transposition_parallel"
)

private fun getFunctionInfo(
    type: UtType,
    name: String,
    module: String,
    typeSystem: PythonTypeSystemWithMypyInfo,
    program: StructuredPyProgram,
): PyUnpinnedCallable? {
    // println("Module: $module, name: $name")
    val description = type.pythonDescription()
    if (description !is PythonCallableTypeDescription) {
        return null
    }
    if (ignoreFunctions.contains(name)) {
        return null
    }
    // if (module != "requests.cookies")
    //    return null
    // if (name != "remove_cookie_by_name")
    //    return null
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

/*
/home/tochilinak/Documents/projects/utbot/Python/dynamic_programming
/home/tochilinak/Documents/projects/utbot/mypy_tmp
/home/tochilinak/Documents/projects/utbot/usvm/usvm-python/cpythonadapter/build/cpython_build/bin/python3
*/

private fun buildProjectRunConfig(): RunConfig {
    val projectPath = "D:\\projects\\Python\\sorts"
    val mypyRoot = "D:\\projects\\mypy_tmp"
    val files = getPythonFilesFromRoot(projectPath).filter { !it.name.contains("__init__") }
    println("Files: $files")
    val modules = getModulesFromFiles(projectPath, files)
    println("Modules: $modules")
    val mypyDir = MypyBuildDirectory(File(mypyRoot), setOf(projectPath))
    buildMypyInfo(
        "D:\\projects\\usvm\\usvm-python\\cpythonadapter\\build\\cpython_build\\python_d.exe",
        files.map { it.canonicalPath },
        modules,
        mypyDir
    )
    val mypyBuild = readMypyInfoBuild(mypyDir)
    val program = StructuredPyProgram(setOf(File(projectPath)))
    val typeSystem = PythonTypeSystemWithMypyInfo(mypyBuild, program)
    val functions = modules.flatMap { module ->
        // println("Module: $module")
        if (module in ignoreModules) {
            return@flatMap emptyList()
        }
        runCatching {
            withAdditionalPaths(program.roots, typeSystem) {
                program.getNamespaceOfModule(module)
            }
        }.getOrNull() ?: return@flatMap emptyList() // skip bad modules
        mypyBuild.definitions[module]!!.flatMap { (defName, def) ->
            // println("Def name: $defName")
            val type = def.getUtBotType()
            val description = type.pythonDescription()
            if (defName.startsWith("__")) {
                emptyList()
            } else if (description is PythonConcreteCompositeTypeDescription) {
                emptyList()
                /*val members = description.getNamedMembers(type)
                members.mapNotNull { memberDef ->
                    if (memberDef.meta.name.startsWith("__"))
                        return@mapNotNull null
                    memberDef.type
                    val name = "$defName.${memberDef.meta.name}"
                    getFunctionInfo(memberDef.type, name, module, typeSystem, program)
                }*/
            } else {
                getFunctionInfo(type, defName, module, typeSystem, program)?.let { listOf(it) } ?: emptyList()
            }
        }
    }
    return RunConfig(program, typeSystem, functions)
}

private fun checkConcolicAndConcrete(runConfig: RunConfig) {
    val (program, typeSystem, functions) = runConfig
    val runner = CustomPythonTestRunner(
        program,
        typeSystem,
        UMachineOptions(stepLimit = 60U, timeout = 60.seconds),
        allowPathDiversions = true
    )
    runner.timeoutPerRunMs = 10_000
    functions.forEach { function ->
        println("Running ${function.tag}...")
        try {
            val comparator = runner.standardConcolicAndConcreteChecks
            when (val argsNum = function.numberOfArguments) {
                0 -> runner.check0NoPredicates(function, comparator)
                1 -> runner.check1NoPredicates(function, comparator)
                2 -> runner.check2NoPredicates(function, comparator)
                3 -> runner.check3NoPredicates(function, comparator)
                4 -> runner.check4NoPredicates(function, comparator)
                else -> println("${function.tag} ignored because it has $argsNum arguments")
            }
        } catch (e: IllegalOperationException) {
            println("Illegal operation while analyzing: ${e.operation}\n")
        }
    }
}

private data class RunConfig(
    val program: PyProgram,
    val typeSystem: PythonTypeSystem,
    val functions: List<PyUnpinnedCallable>,
)
