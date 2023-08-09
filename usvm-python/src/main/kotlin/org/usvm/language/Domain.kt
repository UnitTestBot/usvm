package org.usvm.language

import org.usvm.machine.interpreters.ConcretePythonInterpreter
import org.usvm.machine.interpreters.PythonNamespace
import org.usvm.machine.interpreters.PythonObject
import org.usvm.language.types.PythonType

sealed class PythonProgram {
    abstract fun pinCallable(callable: PythonUnpinnedCallable): PythonPinnedCallable
}

data class PrimitivePythonProgram(val asString: String): PythonProgram() {
    private val namespace = ConcretePythonInterpreter.getNewNamespace()
    init {
        ConcretePythonInterpreter.concreteRun(namespace, asString)
    }
    override fun pinCallable(callable: PythonUnpinnedCallable): PythonPinnedCallable =
        PythonPinnedCallable(callable.reference(namespace))
}

abstract class StructuredPythonProgram: PythonProgram()

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
object NbAddMethod: TypeMethod(false)
object NbMultiplyMethod: TypeMethod(false)
object SqLengthMethod: TypeMethod(true)
object MpSubscriptMethod: TypeMethod(false)
object MpAssSubscriptMethod: TypeMethod(false)
data class TpRichcmpMethod(val op: Int): TypeMethod(false)
object TpIterMethod: TypeMethod(false)