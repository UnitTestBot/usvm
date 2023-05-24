import org.usvm.interpreter.CPythonAdapter

fun main() {
    val lib = CPythonAdapter()
    val mainModule = lib.initializePython()
    try {
        println(mainModule)

        if (mainModule == 0L)
            return

        val res = lib.concreteRun(mainModule, "print('Hello from Python!', flush=True)")
        if (res != 0)
            return
    } finally {
        lib.finalizePython()
    }
}