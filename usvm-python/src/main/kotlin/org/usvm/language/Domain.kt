package org.usvm.language

import org.usvm.interpreter.ConcretePythonInterpreter
import org.usvm.interpreter.PythonNamespace
import org.usvm.interpreter.PythonObject

data class PythonProgram(val asString: String)

class Slot
class Attribute

class Callable(
    val signature: List<PythonType>,
    val reference: (PythonNamespace) -> /* function reference */ PythonObject
) {
    val numberOfArguments: Int = signature.size
    companion object {
        fun constructCallableFromName(signature: List<PythonType>, name: String) =
            Callable(signature) { globals -> ConcretePythonInterpreter.eval(globals, name) }
    }
}