import org.usvm.interpreter.*
import org.usvm.language.PythonProgram
import org.usvm.language.PythonUnpinnedCallable
import org.usvm.language.types.pythonInt
import org.usvm.language.types.pythonList

fun main() {
    val program = PythonProgram(
        """
        def f(y: list, i: int):
            if y[i] == 0:
                return 1
            elif y[i] == 167:
                return 2
            return 3
        """.trimIndent()
    )
    val function = PythonUnpinnedCallable.constructCallableFromName(listOf(pythonList, pythonInt), "f")
    val machine = PythonMachine(program, printErrorMsg = true) { it }
    val start = System.currentTimeMillis()
    val iterations = machine.use { activeMachine ->
        val results: MutableList<PythonAnalysisResult<PythonObject>> = mutableListOf()
        val returnValue = activeMachine.analyze(function, results)
        results.forEach { (_, inputs, result) ->
            println("INPUT:")
            inputs.map { it.reprFromPythonObject }.forEach { ConcretePythonInterpreter.printPythonObject(it) }
            println("RESULT:")
            println((result as? Success)?.output?.let { ConcretePythonInterpreter.getPythonObjectRepr(it) } ?: "Bad execution")
        }
        returnValue
    }
    println("Finished in ${System.currentTimeMillis() - start} milliseconds. Made $iterations iterations.")
}
