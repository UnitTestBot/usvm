import org.usvm.interpreter.*
import org.usvm.language.PythonProgram
import org.usvm.language.PythonUnpinnedCallable
import org.usvm.language.types.PythonAnyType
import org.usvm.language.types.pythonInt
import org.usvm.language.types.pythonList

fun main() {
    val program = PythonProgram(
        """
        import pickle
        def f(x: int):
            y = pickle.loads(pickle.dumps(x))  # y is equal to x
            if y >= 0:
                if x >= 0:
                    return 1
                return 2  # unreachable
            else:
                if x >= 0:
                    return 3  # unreachable
                return 4
        """.trimIndent()
    )
    val function = PythonUnpinnedCallable.constructCallableFromName(listOf(pythonInt), "f")
    val machine = PythonMachine(program, printErrorMsg = true, allowPathDiversion = false) { it }
    val start = System.currentTimeMillis()
    val iterations = machine.use { activeMachine ->
        val results: MutableList<PythonAnalysisResult<PythonObject>> = mutableListOf()
        val returnValue = activeMachine.analyze(function, results)
        results.forEach { (_, inputs, result) ->
            println("INPUT:")
            inputs.map { it.reprFromPythonObject }.forEach { ConcretePythonInterpreter.printPythonObject(it) }
            println("RESULT:")
            when (result) {
                is Success -> println(ConcretePythonInterpreter.getPythonObjectRepr(result.output))
                is Fail -> println(ConcretePythonInterpreter.getNameOfPythonType(result.exception))
            }
            println()
        }
        returnValue
    }
    println("Finished in ${System.currentTimeMillis() - start} milliseconds. Made $iterations iterations.")
}
