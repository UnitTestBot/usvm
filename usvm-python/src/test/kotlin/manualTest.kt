import org.usvm.UMachineOptions
import org.usvm.language.PrimitivePyProgram
import org.usvm.language.PyProgram
import org.usvm.machine.*
import org.usvm.language.PyUnpinnedCallable
import org.usvm.language.StructuredPyProgram
import org.usvm.language.types.*
import org.usvm.machine.interpreters.concrete.ConcretePythonInterpreter
import org.usvm.machine.interpreters.concrete.IllegalOperationException
import org.usvm.machine.results.DefaultPyMachineResultsReceiver
import org.usvm.machine.results.serialization.ObjectWithDictSerializer
import org.usvm.runner.CustomPythonTestRunner
import org.usvm.runner.SamplesBuild
import org.usvm.utils.getModulesFromFiles
import org.usvm.utils.getPythonFilesFromRoot
import org.usvm.machine.utils.withAdditionalPaths
import org.usvm.python.model.PyResultFailure
import org.usvm.python.model.PyResultSuccess
import org.utpython.types.*
import org.utpython.types.general.FunctionType
import org.utpython.types.general.UtType
import org.utpython.types.mypy.MypyBuildDirectory
import org.utpython.types.mypy.buildMypyInfo
import org.utpython.types.mypy.readMypyInfoBuild
import java.io.File
import kotlin.time.Duration.Companion.seconds

fun main() {
    /*val venvConfig = VenvConfig(
        basePath = File("/home/tochilinak/sample_venv/"),
        libPath = File("/home/tochilinak/sample_venv/lib/python3.11/site-packages/"),
        binPath = File("/home/tochilinak/sample_venv/bin")
    )
    ConcretePythonInterpreter.setVenv(venvConfig)*/
    // ConcretePythonInterpreter.printIdInfo()
    // val config = buildProjectRunConfig()
    val config = buildSampleRunConfig()
    analyze(config)
    // checkConcolicAndConcrete(config)
}

private fun buildSampleRunConfig(): RunConfig {
    val (program, typeSystem) = constructStructuredProgram() /*constructPrimitiveProgram(
        """
            def list_concat(x):
                y = x + [1]
                if len(y[::-1]) == 5:
                    return 1
                return 2


            def f(x):
                assert x != "aaaa"
        """.trimIndent()
    )*/
    val function = PyUnpinnedCallable.constructCallableFromName(
        listOf(typeSystem.pythonList),
        "reverse_usage",
        "Lists"
    )
    val functions = listOf(function)
    return RunConfig(program, typeSystem, functions)
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
    program: StructuredPyProgram
): PyUnpinnedCallable? {
    println("Module: $module, name: $name")
    val description = type.pythonDescription()
    if (description !is PythonCallableTypeDescription)
        return null
    if (ignoreFunctions.contains(name))
        return null
    //if (module != "requests.cookies")
    //    return null
    if (name != "remove_cookie_by_name")
        return null
    if (description.argumentKinds.any { it == PythonCallableTypeDescription.ArgKind.ARG_STAR || it == PythonCallableTypeDescription.ArgKind.ARG_STAR_2 })
        return null
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
    val projectPath = "D:\\projects\\requests\\src"
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
        println("Module: $module")
        if (module in ignoreModules)
            return@flatMap emptyList()
        runCatching {
            withAdditionalPaths(program.roots, typeSystem) {
                program.getNamespaceOfModule(module)
            }
        }.getOrNull() ?: return@flatMap emptyList()  // skip bad modules
        mypyBuild.definitions[module]!!.flatMap { (defName, def) ->
            println("Def name: $defName")
            val type = def.getUtBotType()
            val description = type.pythonDescription()
            if (defName.startsWith("__")) {
                emptyList()
            } else if (description is PythonConcreteCompositeTypeDescription) {
                val members = description.getNamedMembers(type)
                members.mapNotNull { memberDef ->
                    if (memberDef.meta.name.startsWith("__"))
                        return@mapNotNull null
                    memberDef.type
                    val name = "$defName.${memberDef.meta.name}"
                    getFunctionInfo(memberDef.type, name, module, typeSystem, program)
                }
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

private fun analyze(runConfig: RunConfig) {
    val (program, typeSystem, functions) = runConfig
    val machine = PyMachine(program, typeSystem, printErrorMsg = false)
    val emptyCoverage = mutableListOf<String>()
    machine.use { activeMachine ->
        functions.forEach { f ->
            println("Started analysing function ${f.tag}")
            try {
                val start = System.currentTimeMillis()
                val saver = DefaultPyMachineResultsReceiver(ObjectWithDictSerializer)
                val iterations = activeMachine.analyze(
                    f,
                    saver,
                    maxIterations = 90,
                    allowPathDiversion = true,
                    maxInstructions = 50_000,
                    timeoutPerRunMs = 4_000,
                    timeoutMs = 60_000
                )
                saver.pyTestObserver.tests.forEach { test ->
                    println("INPUT:")
                    test.inputArgs.forEach { println(it) }
                    println("RESULT:")
                    when (val result = test.result) {
                        is PyResultSuccess -> println(result.output)
                        is PyResultFailure -> println(result.exception)
                    }
                    println()
                }
                if (machine.statistics.functionStatistics.last().coverage == 0.0) {
                    emptyCoverage.add(f.tag)
                }
                println("Finished analysing ${f.tag} in ${System.currentTimeMillis() - start} milliseconds. Made $iterations iterations.")
                println("FUNCTION STATISTICS")
                println(machine.statistics.functionStatistics.last().writeReport())
                println()
            } catch (e: IllegalOperationException) {
                println("Illegal operation while analyzing: ${e.operation}\n")
            }
        }
        println("GENERAL STATISTICS")
        println(machine.statistics.writeReport())
    }
    println()
    println("Empty coverage for:")
    emptyCoverage.forEach { println(it) }
}

private data class RunConfig(
    val program: PyProgram,
    val typeSystem: PythonTypeSystem,
    val functions: List<PyUnpinnedCallable>
)

@Suppress("SameParameterValue")
private fun constructPrimitiveProgram(asString: String): Pair<PyProgram, PythonTypeSystem> {
    val program = PrimitivePyProgram.fromString(asString)
    val typeSystem = BasicPythonTypeSystem()
    return Pair(program, typeSystem)
}

@Suppress("SameParameterValue")
private fun constructPrimitiveProgramFromStructured(module: String): Pair<PyProgram, PythonTypeSystem> {
    val program = SamplesBuild.program.getPrimitiveProgram(module)
    val typeSystem = BasicPythonTypeSystem()
    return Pair(program, typeSystem)
}

@Suppress("SameParameterValue")
private fun constructStructuredProgram(): Pair<PyProgram, PythonTypeSystem> {
    val program = SamplesBuild.program
    val typeSystem = PythonTypeSystemWithMypyInfo(SamplesBuild.mypyBuild, program)
    return Pair(program, typeSystem)
}