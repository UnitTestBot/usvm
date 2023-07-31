package org.usvm.language

import org.usvm.interpreter.ConcretePythonInterpreter
import org.usvm.interpreter.PythonNamespace
import org.usvm.interpreter.PythonObject
import org.usvm.language.types.PythonType

data class PythonProgram(val asString: String)

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

sealed class TypeMethod(val isMethodWithNonVirtualReturn: Boolean): PythonCallable()

object NbBoolMethod: TypeMethod(true)
object NbIntMethod: TypeMethod(true)
object MpSubscriptMethod: TypeMethod(false)
data class TpRichcmpMethod(val op: Int): TypeMethod(false)