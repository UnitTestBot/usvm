import org.usvm.language.PrimitivePythonProgram
import org.usvm.language.PythonProgram
import org.usvm.machine.*
import org.usvm.language.PythonUnpinnedCallable
import org.usvm.language.types.*
import org.usvm.machine.interpreters.ConcretePythonInterpreter
import org.usvm.runner.SamplesBuild

fun main() {
    println("Initial sys.path:")
    System.out.flush()
    ConcretePythonInterpreter.printPythonObject(ConcretePythonInterpreter.initialSysPath)
    val (program, function, typeSystem) = constructStructuredProgram(
        "sample_submodule.SimpleUsageOfModules",
        listOf(PythonAnyType),
        "simple_class_isinstance"
    )
    println("sys.path before analysis:")
    System.out.flush()
    val namespace = ConcretePythonInterpreter.getNewNamespace()
    ConcretePythonInterpreter.concreteRun(namespace, "import sys")
    ConcretePythonInterpreter.printPythonObject(ConcretePythonInterpreter.eval(namespace, "sys.path"))
    ConcretePythonInterpreter.decref(namespace)

    println("Initial sys.path:")
    System.out.flush()
    ConcretePythonInterpreter.printPythonObject(ConcretePythonInterpreter.initialSysPath)

    val machine = PythonMachine(program, typeSystem, ReprObjectSerializer, printErrorMsg = true)
    val start = System.currentTimeMillis()
    val iterations = machine.use { activeMachine ->
        val results: MutableList<PythonAnalysisResult<String>> = mutableListOf()
        val returnValue = activeMachine.analyze(function, results, maxIterations = 15, allowPathDiversion = true)
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
    val target: PythonUnpinnedCallable,
    val typeSystem: PythonTypeSystem
)

@Suppress("SameParameterValue")
private fun constructPrimitiveProgram(
    asString: String,
    signature: List<PythonType>,
    functionName: String
): RunConfig {
    val program = PrimitivePythonProgram.fromString(asString)
    val function = PythonUnpinnedCallable.constructCallableFromName(signature, functionName)
    val typeSystem = BasicPythonTypeSystem()
    return RunConfig(program, function, typeSystem)
}

@Suppress("SameParameterValue")
private fun constructPrimitiveProgramFromStructured(
    module: String,
    signature: List<PythonType>,
    functionName: String
): RunConfig {
    val program = SamplesBuild.program.getPrimitiveProgram(module)
    val function = PythonUnpinnedCallable.constructCallableFromName(signature, functionName)
    val typeSystem = BasicPythonTypeSystem()
    return RunConfig(program, function, typeSystem)
}

@Suppress("SameParameterValue")
private fun constructStructuredProgram(
    module: String,
    signature: List<PythonType>,
    functionName: String
): RunConfig {
    val program = SamplesBuild.program
    val function = PythonUnpinnedCallable.constructCallableFromName(signature, functionName, module)
    val typeSystem = PythonTypeSystemWithMypyInfo(SamplesBuild.mypyBuild, program)
    return RunConfig(program, function, typeSystem)
}