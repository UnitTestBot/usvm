import org.usvm.interpreter.*

fun main() {
    val investigator = ConcretePythonInterpreter.createInvestigatorObject()
    ConcretePythonInterpreter.printPythonObject(investigator)
    /*
    ConcretePythonInterpreter.printPythonObject(pythonNoneType.asObject)
    ConcretePythonInterpreter.printPythonObject(pythonObjectType.asObject)
    val namespace = ConcretePythonInterpreter.getNewNamespace()
    ConcretePythonInterpreter.concreteRun(
        namespace,
        """
        class A:
            pass

        class B:
            def __init__(self, x):
                self.x = x

        class C:
            def __new__(cls):
                pass

        class D:
            def __new__(cls, x):
                pass
        """.trimIndent()
    )
    val classA = ConcretePythonInterpreter.eval(namespace, "A")
    val classB = ConcretePythonInterpreter.eval(namespace, "B")
    val classC = ConcretePythonInterpreter.eval(namespace, "C")
    val classD = ConcretePythonInterpreter.eval(namespace, "D")
    ConcretePythonInterpreter.printPythonObject(classA)
    ConcretePythonInterpreter.printPythonObject(classB)
    ConcretePythonInterpreter.printPythonObject(classC)
    ConcretePythonInterpreter.printPythonObject(classD)
     */
    /*
    val program = PythonProgram(
        """
        def f(x: bool, y: int):
            if x and y % 10 == 5:
                return 1
            elif not x:
                return 2
            else:
                return 3
        """.trimIndent()
    )
    val function = PythonUnpinnedCallable.constructCallableFromName(listOf(pythonBool, pythonInt), "f")
    val machine = PythonMachine(program, printErrorMsg = true) { it }
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
     */
}
