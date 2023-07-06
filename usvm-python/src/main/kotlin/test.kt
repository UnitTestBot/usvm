import org.usvm.interpreter.*
import org.usvm.language.pythonInt
import org.usvm.language.PythonProgram
import org.usvm.language.PythonUnpinnedCallable
import org.usvm.language.pythonBool

fun main() {
    val program = PythonProgram(
        """
        def f(x: bool, y: int):
            if x and y % 10 == 5:
                return 1
            elif not x:
                return 2
            else:
                return 3
        """.trimIndent()
    )
    val function = PythonUnpinnedCallable.constructCallableFromName(listOf(pythonBool, pythonInt), "f")
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
