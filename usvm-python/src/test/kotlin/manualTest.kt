import org.usvm.language.PrimitivePythonProgram
import org.usvm.language.PythonProgram
import org.usvm.machine.*
import org.usvm.language.PythonUnpinnedCallable
import org.usvm.language.StructuredPythonProgram
import org.usvm.language.types.*
import org.usvm.machine.interpreters.ConcretePythonInterpreter
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

private fun buildSampleRunConfig(): RunConfig {
    val (program, typeSystem) = constructPrimitiveProgram(
        """ 
        def f(x):
            t = 1, x
            a, b = t
            assert a == b

        """.trimIndent()
    )
    val function = PythonUnpinnedCallable.constructCallableFromName(
        listOf(typeSystem.pythonInt),
        "f"
    )
    val functions = listOf(function)
    return RunConfig(program, typeSystem, functions)
}

private fun buildProjectRunConfig(): RunConfig {
    val projectPath = "/home/tochilinak/Documents/projects/utbot/Python/divide_and_conquer"
    val mypyRoot = "/home/tochilinak/Documents/projects/utbot/mypy_tmp"
    val files = getPythonFilesFromRoot(projectPath)
    val modules = getModulesFromFiles(projectPath, files)
    val mypyDir = MypyBuildDirectory(File(mypyRoot), setOf(projectPath))
    buildMypyInfo("python3.10", files.map { it.canonicalPath }, modules, mypyDir)
    val mypyBuild = readMypyInfoBuild(mypyDir)
    val program = StructuredPythonProgram(setOf(File(projectPath)))
    val typeSystem = PythonTypeSystemWithMypyInfo(mypyBuild, program)
    val ignoreFunctions = emptyList<String>()
    val functions = modules.flatMap { module ->
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
            if (ignoreFunctions.contains(functionName))  // for now
                return@mapNotNull null
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

fun main() {
    // val config = buildProjectRunConfig()
    val config = buildSampleRunConfig()
    analyze(config)
}

private fun analyze(runConfig: RunConfig) {
    val (program, typeSystem, functions) = runConfig
    val machine = PythonMachine(program, typeSystem, ReprObjectSerializer, printErrorMsg = false)
    machine.use { activeMachine ->
        functions.forEach { f ->
            println("Started analysing function ${f.tag}")
            val start = System.currentTimeMillis()
            val results: MutableList<PythonAnalysisResult<String>> = mutableListOf()
            val iterations = activeMachine.analyze(f, results, maxIterations = 30, allowPathDiversion = true, maxInstructions = 1000)
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