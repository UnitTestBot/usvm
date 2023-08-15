import org.usvm.language.PrimitivePythonProgram
import org.usvm.language.PythonProgram
import org.usvm.machine.*
import org.usvm.language.PythonUnpinnedCallable
import org.usvm.language.types.*
import org.usvm.runner.SamplesBuild
import org.usvm.utils.ReprObjectSerializer

fun main() {
    val (program, typeSystem) = constructStructuredProgram() //constructPrimitiveProgramFromStructured("SimpleTypeInference")
    val function = PythonUnpinnedCallable.constructCallableFromName(
        listOf(PythonAnyType),
        "pickle_path_diversion",
        "TrickyExample"
    )

    val machine = PythonMachine(program, typeSystem, ReprObjectSerializer, printErrorMsg = false)
    val start = System.currentTimeMillis()
    val iterations = machine.use { activeMachine ->
        val results: MutableList<PythonAnalysisResult<String>> = mutableListOf()
        val returnValue = activeMachine.analyze(function, results, maxIterations = 20, allowPathDiversion = true)
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
        returnValue
    }
    println("Finished in ${System.currentTimeMillis() - start} milliseconds. Made $iterations iterations.")
}

private data class RunConfig(
    val program: PythonProgram,
    val typeSystem: PythonTypeSystem
)

@Suppress("SameParameterValue")
private fun constructPrimitiveProgram(asString: String): RunConfig {
    val program = PrimitivePythonProgram.fromString(asString)
    val typeSystem = BasicPythonTypeSystem()
    return RunConfig(program, typeSystem)
}

@Suppress("SameParameterValue")
private fun constructPrimitiveProgramFromStructured(module: String): RunConfig {
    val program = SamplesBuild.program.getPrimitiveProgram(module)
    val typeSystem = BasicPythonTypeSystem()
    return RunConfig(program, typeSystem)
}

@Suppress("SameParameterValue")
private fun constructStructuredProgram(): RunConfig {
    val program = SamplesBuild.program
    val typeSystem = PythonTypeSystemWithMypyInfo(SamplesBuild.mypyBuild, program)
    return RunConfig(program, typeSystem)
}