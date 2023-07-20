import org.usvm.interpreter.*
import org.usvm.language.PythonProgram
import org.usvm.language.PythonUnpinnedCallable
import org.usvm.language.types.PythonAnyType
import org.usvm.language.types.pythonBool
import org.usvm.language.types.pythonInt

fun main() {
    val program = PythonProgram(
        """
        def f(x, y):
            if x and y:
                return 1
            elif x:
                return 2
            elif y:
                return 3
            else:
                return 4
        """.trimIndent()
    )
    val function = PythonUnpinnedCallable.constructCallableFromName(listOf(PythonAnyType, PythonAnyType), "f")
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
