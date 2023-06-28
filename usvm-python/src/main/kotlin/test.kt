import org.usvm.interpreter.PythonMachine
import org.usvm.language.PythonCallable
import org.usvm.language.PythonInt
import org.usvm.language.PythonProgram

fun main() {
    //val globals = ConcretePythonInterpreter.getNewNamespace()
    //ConcretePythonInterpreter.concreteRun(globals, "x = 10 ** 100")
    //ConcretePythonInterpreter.concreteRun(globals, "print('Hello from Python!\\nx is', x, flush=True)")

    val program = PythonProgram(
        """
        import pickle
        def f(x):
            print("x:", x, flush=True)
            if int(x) >= 0:
                if x >= 0:
                    return pickle.dumps(x)
                else:
                    return pickle.dumps(-x)
            else:
                return 1
        """.trimIndent()
    )
    val function = PythonCallable.constructCallableFromName(List(1) { PythonInt }, "f")
    val machine = PythonMachine(program)
    val start = System.currentTimeMillis()
    val iterations = machine.use { it.analyze(function) }
    println("Finished in ${System.currentTimeMillis() - start} milliseconds. Made $iterations iterations.")
    println("${machine.solver.cnt}")
}
