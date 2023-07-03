package org.usvm.language

import org.usvm.interpreter.ConcretePythonInterpreter
import org.usvm.interpreter.PythonNamespace
import org.usvm.interpreter.PythonObject

data class PythonProgram(val asString: String)

class Slot
class Attribute

sealed class PythonCallable

data class PythonPinnedCallable(val asPythonObject: PythonObject): PythonCallable()

class PythonUnpinnedCallable(
    val signature: List<PythonType>,
    val reference: (PythonNamespace) -> /* function reference */ PythonObject
): PythonCallable() {
    val numberOfArguments: Int = signature.size
    companion object {
        fun constructCallableFromName(signature: List<PythonType>, name: String) =
            PythonUnpinnedCallable(signature) { globals -> ConcretePythonInterpreter.eval(globals, name) }
    }
}