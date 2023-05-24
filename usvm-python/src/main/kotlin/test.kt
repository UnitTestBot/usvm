import org.usvm.interpreter.ConcretePythonInterpreter

fun main() {
    val globals = ConcretePythonInterpreter.getNewNamespace()
    ConcretePythonInterpreter.concreteRun(globals, "x = 10 ** 100")
    ConcretePythonInterpreter.concreteRun(globals, "print('Hello from Python!\\nx is', x, flush=True)")
}