import org.usvm.interpreter.*
import org.usvm.language.PythonInt
import org.usvm.language.PythonProgram
import org.usvm.language.PythonUnpinnedCallable

fun main() {
    val program = PythonProgram(
        """
        def g(x):
            if x > 0:
                return x
            else:
                return -x

        def f(x):
            if g(x) == 0:
                return 1
            return 2
        """.trimIndent()
    )
    val function = PythonUnpinnedCallable.constructCallableFromName(List(1) { PythonInt }, "f")

    val namespace = ConcretePythonInterpreter.getNewNamespace()
    ConcretePythonInterpreter.concreteRun(namespace, program.asString)
    val functionRef = function.reference(namespace)
    val args = listOf(ConcretePythonInterpreter.eval(namespace, "1"))
    val result = ConcretePythonInterpreter.concreteRunOnFunctionRef(namespace, functionRef, args)
    println("RESULT OF CONCRETE RUN: ${ConcretePythonInterpreter.getPythonObjectRepr(result)}")

    val machine = PythonMachine(program) { it }
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
