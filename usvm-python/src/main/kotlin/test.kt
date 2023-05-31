import org.usvm.interpreter.ConcretePythonInterpreter
import org.usvm.interpreter.PythonMachine
import org.usvm.language.Callable
import org.usvm.language.PythonProgram

fun main() {
    val globals = ConcretePythonInterpreter.getNewNamespace()
    ConcretePythonInterpreter.concreteRun(globals, "x = 10 ** 100")
    ConcretePythonInterpreter.concreteRun(globals, "print('Hello from Python!\\nx is', x, flush=True)")

    val program = PythonProgram(
        """
            import time
            def f(x):
                #start = time.time()
                #y = []
                #while len(y) < 10**6: y += [1]
                #print("TIME", time.time() - start, flush=True)
                []
                return x*2 if x else -x*2
        """.trimIndent()
    )
    val function = Callable.constructCallableFromName(1, "f")
    val machine = PythonMachine(program)
    machine.use { it.analyze(function) }
}