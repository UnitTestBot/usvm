import org.usvm.interpreter.ConcretePythonInterpreter
import org.usvm.interpreter.PythonMachine
import org.usvm.language.PythonCallable
import org.usvm.language.PythonInt
import org.usvm.language.PythonProgram

fun main() {
    val program = PythonProgram(
        """
        import pickle
        def f(x):
            if x >= 0:
                return x
            else:
                return -x
            ${"\"\"\""}
            print("x:", x, flush=True)
            if int(x) >= 0:
                if x >= 0:
                    return pickle.dumps(x)
                else:
                    return pickle.dumps(-x)
            else:
                return 1
            ${"\"\"\""}
        """.trimIndent()
    )
    val function = PythonCallable.constructCallableFromName(List(1) { PythonInt }, "f")
    val machine = PythonMachine(program) { it }
    val start = System.currentTimeMillis()
    val iterations = machine.use { activeMachine ->
        activeMachine.analyze(function)
        activeMachine.results.forEach { (inputs, result) ->
            println("INPUT:")
            inputs.forEach { ConcretePythonInterpreter.printPythonObject(it) }
            println("RESULT:")
            ConcretePythonInterpreter.printPythonObject(result!!)
        }
    }
    println("Finished in ${System.currentTimeMillis() - start} milliseconds. Made $iterations iterations.")
}
