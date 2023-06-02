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
            def f(x, y):
                #start = time.time()
                #y = []
                #while len(y) < 10**6: y += [1]
                #print("TIME", time.time() - start, flush=True)
                if x > y:
                    return 1
                elif 0 > x:
                    return 2
                elif 10 > y:
                    return 3
                elif y > x:
                    return 4
                return 5
        """.trimIndent()
    )
    val function = Callable.constructCallableFromName(2, "f")
    val machine = PythonMachine(program)
    machine.use { it.analyze(function) }
}