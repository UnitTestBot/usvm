import org.usvm.UMachineOptions
import org.usvm.language.PrimitivePythonProgram
import org.usvm.language.PythonProgram
import org.usvm.machine.*
import org.usvm.language.PythonUnpinnedCallable
import org.usvm.language.StructuredPythonProgram
import org.usvm.language.types.*
import org.usvm.machine.interpreters.ConcretePythonInterpreter
import org.usvm.machine.interpreters.IllegalOperationException
import org.usvm.machine.interpreters.emptyNamespace
import org.usvm.runner.CustomPythonTestRunner
import org.usvm.runner.SamplesBuild
import org.usvm.utils.ReprObjectSerializer
import org.usvm.utils.getModulesFromFiles
import org.usvm.utils.getPythonFilesFromRoot
import org.usvm.utils.withAdditionalPaths
import org.utbot.python.newtyping.PythonCallableTypeDescription
import org.utbot.python.newtyping.mypy.MypyBuildDirectory
import org.utbot.python.newtyping.mypy.buildMypyInfo
import org.utbot.python.newtyping.mypy.readMypyInfoBuild
import org.utbot.python.newtyping.pythonDescription
import org.utbot.python.newtyping.pythonTypeRepresentation
import java.io.File

fun main() {
    // val slice = ConcretePythonInterpreter.eval(emptyNamespace, "slice")
    // println(ConcretePythonInterpreter.typeLookup(slice, "start"))
    // val config = buildProjectRunConfig()
    val config = buildSampleRunConfig()
    analyze(config)
    // checkConcolicAndConcrete(config)
}

private fun buildSampleRunConfig(): RunConfig {
    val (program, typeSystem) = constructStructuredProgram()
    val function = PythonUnpinnedCallable.constructCallableFromName(
        listOf(typeSystem.pythonInt, typeSystem.pythonInt, typeSystem.pythonInt),
        "slice_usages",
        "Slices"
    )
    val functions = listOf(function)
    return RunConfig(program, typeSystem, functions)
}

private fun buildProjectRunConfig(): RunConfig {
    val projectPath = "/home/tochilinak/Documents/projects/utbot/Python/sorts"
    val mypyRoot = "/home/tochilinak/Documents/projects/utbot/mypy_tmp"
    val files = getPythonFilesFromRoot(projectPath)
    val modules = getModulesFromFiles(projectPath, files)
    val mypyDir = MypyBuildDirectory(File(mypyRoot), setOf(projectPath))
    buildMypyInfo(
        "/home/tochilinak/Documents/projects/utbot/usvm/usvm-python/cpythonadapter/build/cpython_build/bin/python3",
        files.map { it.canonicalPath },
        modules,
        mypyDir
    )
    val mypyBuild = readMypyInfoBuild(mypyDir)
    val program = StructuredPythonProgram(setOf(File(projectPath)))
    val typeSystem = PythonTypeSystemWithMypyInfo(mypyBuild, program)
    val ignoreFunctions = listOf<String>(
        "circle_sort",  // NoSuchElement
        "cocktail_shaker_sort",  // slow (why?)
        "quick_sort_lomuto_partition",  // NoSuchElement
        "oe_process",  // blocks
        "merge_insertion_sort",  // slow (why?)
        "msd_radix_sort_inplace"  // NoSuchElement
    )
    val ignoreModules = listOf<String>(
        // "heaps_algorithm",
        "intro_sort",  // NoSuchElement
        "heap_sort"  // NoSuchElement
    )
    val functions = modules.flatMap { module ->
        if (module in ignoreModules)
            return@flatMap emptyList()
        runCatching {
            withAdditionalPaths(program.roots, typeSystem) {
                program.getNamespaceOfModule(module)
            }
        }.getOrNull() ?: return@flatMap emptyList()  // skip bad modules
        mypyBuild.definitions[module]!!.mapNotNull { (functionName, def) ->
            val type = def.getUtBotType()
            val description = type.pythonDescription()
            if (description !is PythonCallableTypeDescription)
                return@mapNotNull null
            if (ignoreFunctions.contains(functionName))
                return@mapNotNull null
            // if (functionName != "peak")
            //     return@mapNotNull null
            println("$module.$functionName: ${type.pythonTypeRepresentation()}")
            PythonUnpinnedCallable.constructCallableFromName(
                List(description.numberOfArguments) { PythonAnyType },
                functionName,
                module
            )
        }
    }
    return RunConfig(program, typeSystem, functions)
}

private fun checkConcolicAndConcrete(runConfig: RunConfig) {
    val (program, typeSystem, functions) = runConfig
    val runner = CustomPythonTestRunner(
        program,
        typeSystem,
        UMachineOptions(stepLimit = 60U, timeoutMs = 60_000),
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
    val machine = PythonMachine(program, typeSystem, ReprObjectSerializer, printErrorMsg = false)
    machine.use { activeMachine ->
        functions.forEach { f ->
            println("Started analysing function ${f.tag}")
            try {
                val start = System.currentTimeMillis()
                val results: MutableList<PythonAnalysisResult<String>> = mutableListOf()
                val iterations = activeMachine.analyze(
                    f,
                    results,
                    maxIterations = 100,
                    allowPathDiversion = true,
                    maxInstructions = 15_000,
                    //timeoutPerRunMs = 5_000,
                    //timeoutMs = 20_000
                )
                results.forEach { (_, inputs, result) ->
                    println("INPUT:")
                    inputs.map { it.reprFromPythonObject }.forEach { println(it) }
                    println("RESULT:")
                    when (result) {
                        is Success -> println(result.output)
                        is Fail -> println(result.exception)
                    }
                    println()
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
}

private data class RunConfig(
    val program: PythonProgram,
    val typeSystem: PythonTypeSystem,
    val functions: List<PythonUnpinnedCallable>
)

@Suppress("SameParameterValue")
private fun constructPrimitiveProgram(asString: String): Pair<PythonProgram, PythonTypeSystem> {
    val program = PrimitivePythonProgram.fromString(asString)
    val typeSystem = BasicPythonTypeSystem()
    return Pair(program, typeSystem)
}

@Suppress("SameParameterValue")
private fun constructPrimitiveProgramFromStructured(module: String): Pair<PythonProgram, PythonTypeSystem> {
    val program = SamplesBuild.program.getPrimitiveProgram(module)
    val typeSystem = BasicPythonTypeSystem()
    return Pair(program, typeSystem)
}

@Suppress("SameParameterValue")
private fun constructStructuredProgram(): Pair<PythonProgram, PythonTypeSystem> {
    val program = SamplesBuild.program
    val typeSystem = PythonTypeSystemWithMypyInfo(SamplesBuild.mypyBuild, program)
    return Pair(program, typeSystem)
}