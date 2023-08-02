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
            i = 0
            sum_ = 0
            while i < len(x):
                sum_ += x[i]
                i += 1
            if sum_ == 100:
                return 1
            elif sum_ % 200 == 153:
                return 2
            return 3
        """.trimIndent()
    )
    val function = PythonUnpinnedCallable.constructCallableFromName(listOf(pythonList), "f")
    val machine = PythonMachine(program, printErrorMsg = true, allowPathDiversion = true) { it }
    val start = System.currentTimeMillis()
    val iterations = machine.use { activeMachine ->
        val results: MutableList<PythonAnalysisResult<PythonObject>> = mutableListOf()
        val returnValue = activeMachine.analyze(function, results, maxIterations = 10)
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
