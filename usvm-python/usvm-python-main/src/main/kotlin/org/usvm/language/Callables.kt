package org.usvm.language

import org.usvm.machine.interpreters.concrete.ConcretePythonInterpreter
import org.usvm.machine.interpreters.concrete.PyNamespace
import org.usvm.machine.interpreters.concrete.PyObject
import org.usvm.machine.types.PythonType

sealed class PyCallable

data class PyPinnedCallable(val asPyObject: PyObject): PyCallable()

class PyUnpinnedCallable(
    val signature: List<PythonType>,
    val module: String?,
    val tag: String,
    val reference: (PyNamespace) -> /* function reference */ PyObject
): PyCallable() {
    val numberOfArguments: Int = signature.size
    companion object {
        fun constructCallableFromName(signature: List<PythonType>, name: String, module: String? = null) =
            PyUnpinnedCallable(signature, module, "$module.$name") { globals -> ConcretePythonInterpreter.eval(globals, name) }

        fun constructLambdaFunction(signature: List<PythonType>, expr: String) =
            PyUnpinnedCallable(signature, null, "lambda \"$expr\"") { globals -> ConcretePythonInterpreter.eval(globals, expr) }
    }
}

sealed class TypeMethod(val isMethodWithNonVirtualReturn: Boolean): PyCallable()

object NbBoolMethod: TypeMethod(true)
object NbIntMethod: TypeMethod(true)
object NbAddMethod: TypeMethod(false)
object NbSubtractMethod: TypeMethod(false)
object NbMultiplyMethod: TypeMethod(false)
object NbMatrixMultiplyMethod: TypeMethod(false)
object NbNegativeMethod: TypeMethod(false)
object NbPositiveMethod: TypeMethod(false)
object SqLengthMethod: TypeMethod(true)
object MpSubscriptMethod: TypeMethod(false)
object MpAssSubscriptMethod: TypeMethod(false)
data class TpRichcmpMethod(val op: Int): TypeMethod(false)
object TpGetattro: TypeMethod(false)
object TpSetattro: TypeMethod(false)
object TpIterMethod: TypeMethod(false)
object TpCallMethod: TypeMethod(false)