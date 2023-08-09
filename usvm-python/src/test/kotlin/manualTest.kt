import org.usvm.language.PrimitivePythonProgram
import org.usvm.machine.*
import org.usvm.language.PythonUnpinnedCallable
import org.usvm.language.types.*
import org.usvm.machine.interpreters.ConcretePythonInterpreter
import org.usvm.samples.SamplesBuild

@Suppress("SameParameterValue")
private fun constructPrimitiveProgram(
    asString: String,
    signature: List<PythonType>,
    functionName: String
): Pair<PrimitivePythonProgram, PythonUnpinnedCallable> {
    val program = PrimitivePythonProgram.fromString(asString)
    val function = PythonUnpinnedCallable.constructCallableFromName(signature, functionName)
    return program to function
}

@Suppress("SameParameterValue")
private fun constructPrimitiveProgramFromStructured(
    module: String,
    signature: List<PythonType>,
    functionName: String
): Pair<PrimitivePythonProgram, PythonUnpinnedCallable> {
    val program = SamplesBuild.program.getPrimitiveProgram(module)
    val function = PythonUnpinnedCallable.constructCallableFromName(signature, functionName)
    return program to function
}

fun main() {
    println("Initial sys.path:")
    System.out.flush()
    ConcretePythonInterpreter.printPythonObject(ConcretePythonInterpreter.initialSysPath)
    val (program, function) = constructPrimitiveProgramFromStructured(
        "sample_submodule.SimpleUsageOfModules",
        listOf(pythonInt),
        "inner_import"
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

    val typeSystem = PythonTypeSystem()
    val machine = PythonMachine(program, typeSystem, printErrorMsg = true) {
        ConcretePythonInterpreter.getPythonObjectRepr(it)
    }
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
