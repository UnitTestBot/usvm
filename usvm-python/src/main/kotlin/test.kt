import org.usvm.interpreter.ConcretePythonInterpreter
import org.usvm.interpreter.PythonAnalysisResult
import org.usvm.interpreter.PythonMachine
import org.usvm.interpreter.PythonObject
import org.usvm.language.PythonCallable
import org.usvm.language.PythonInt
import org.usvm.language.PythonProgram

fun main() {
    val program = PythonProgram(
        """
        import pickle
        def f(x):
            if x >= 0:
                return pickle.dumps(x)
            else:
                return pickle.dumps(-x)
        """.trimIndent()
    )
    val function = PythonCallable.constructCallableFromName(List(1) { PythonInt }, "f")

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
        results.forEach { (inputs, result) ->
            println("INPUT:")
            inputs.map { it.reprFromPythonObject }.forEach { ConcretePythonInterpreter.printPythonObject(it) }
            println("RESULT:")
            println(ConcretePythonInterpreter.getPythonObjectRepr(result!!))
        }
        returnValue
    }
    println("Finished in ${System.currentTimeMillis() - start} milliseconds. Made $iterations iterations.")
}
