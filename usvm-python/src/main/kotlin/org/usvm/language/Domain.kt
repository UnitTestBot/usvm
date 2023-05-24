package org.usvm.language

import org.usvm.interpreter.ConcretePythonInterpreter
import org.usvm.interpreter.PythonNamespace
import org.usvm.interpreter.PythonObject

data class Program(val asString: String)

class Slot
class Attribute

class Callable(
    val numberOfArguments: Int,
    val reference: (PythonNamespace) -> /* function reference */ PythonObject
) {
    companion object {
        fun constructCallableFromName(numberOfArguments: Int, name: String) =
            Callable(numberOfArguments) { globals -> ConcretePythonInterpreter.eval(globals, name) }
    }
}