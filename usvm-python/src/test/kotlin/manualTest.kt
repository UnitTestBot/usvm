import org.usvm.interpreter.*
import org.usvm.language.PythonProgram
import org.usvm.language.PythonUnpinnedCallable
import org.usvm.language.types.PythonAnyType
import org.usvm.language.types.pythonInt
import org.usvm.language.types.pythonList

fun main() {
    val program = PythonProgram(
        """
        def f(x):
            if isinstance(x, bool):
                return 1
            if isinstance(x, int):
                return 2
            elif isinstance(x, list):
                return 3
            elif isinstance(x, object):
                return 4
            return "Not reachable"
        """.trimIndent()
    )
    val function = PythonUnpinnedCallable.constructCallableFromName(listOf(PythonAnyType), "f")
    val machine = PythonMachine(program, printErrorMsg = true, allowPathDiversion = true) {
        ConcretePythonInterpreter.getPythonObjectRepr(it)
    }
    val start = System.currentTimeMillis()
    val iterations = machine.use { activeMachine ->
        val results: MutableList<PythonAnalysisResult<String>> = mutableListOf()
        val returnValue = activeMachine.analyze(function, results, maxIterations = 20)
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
