import org.usvm.interpreter.*
import org.usvm.language.PythonInt
import org.usvm.language.PythonProgram
import org.usvm.language.PythonUnpinnedCallable

fun main() {
    val program = PythonProgram(
        """
        def f(x):
            return x / 0
        """.trimIndent()
    )
    val function = PythonUnpinnedCallable.constructCallableFromName(List(1) { PythonInt }, "f")
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
