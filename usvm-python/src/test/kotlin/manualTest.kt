import org.usvm.language.PrimitivePythonProgram
import org.usvm.machine.*
import org.usvm.language.PythonUnpinnedCallable
import org.usvm.language.types.PythonAnyType
import org.usvm.language.types.PythonTypeSystem
import org.usvm.language.types.pythonInt
import org.usvm.language.types.pythonList
import org.usvm.machine.interpreters.ConcretePythonInterpreter

fun main() {
    println("Initial sys.path:")
    System.out.flush()
    ConcretePythonInterpreter.printPythonObject(ConcretePythonInterpreter.initialSysPath)
    val program = PrimitivePythonProgram(
        """
        def f(x: list, y: list):
            x[0][0] += 1
            assert x < y
        """.trimIndent()
    )
    val function = PythonUnpinnedCallable.constructCallableFromName(listOf(pythonList, pythonList), "f")
    val typeSystem = PythonTypeSystem()
    val machine = PythonMachine(program, typeSystem, printErrorMsg = true, allowPathDiversion = true) {
        ConcretePythonInterpreter.getPythonObjectRepr(it)
    }
    val start = System.currentTimeMillis()
    val iterations = machine.use { activeMachine ->
        val results: MutableList<PythonAnalysisResult<String>> = mutableListOf()
        val returnValue = activeMachine.analyze(function, results, maxIterations = 15)
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
