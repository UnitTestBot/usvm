package org.usvm.language

import org.usvm.machine.interpreters.concrete.ConcretePythonInterpreter
import org.usvm.machine.interpreters.concrete.PyNamespace
import org.usvm.machine.interpreters.concrete.PyObject
import org.usvm.machine.types.PythonType

/**
 * Subclasses of [PyCallable] are different ways to represent Python functions.
 * */
sealed class PyCallable

/**
 * [PyPinnedCallable] is a reference to Python object of type `function`.
 * This reference changes between restarts of Python interpreter.
 * */
data class PyPinnedCallable(val pyObject: PyObject) : PyCallable() {
    init {
        val type = ConcretePythonInterpreter.getPythonObjectTypeName(pyObject)
        check(type == "function") {
            "Unexpected type of object in PyPinnedCallable: $type"
        }
    }
}

/**
 * Like [PyPinnedCallable], but for code object.
 * [PyCodeObject] can be constructed from [PyPinnedCallable], but not vice versa.
 * [PyCodeObject], unlike [PyPinnedCallable], cannot be executed with [ConcretePythonInterpreter].
 * [PyCodeObject] can be extracted from [PyInstruction], [PyPinnedCallable] cannot.
 * */
data class PyCodeObject(val codeObject: PyObject) : PyCallable() {
    init {
        val type = ConcretePythonInterpreter.getPythonObjectTypeName(codeObject)
        check(type == "code") {
            "Unexpected type of object in PyCodeObject: $type"
        }
    }
}

/**
 * [PyUnpinnedCallable] is a description of Python function that stays constant
 * between restarts of Python interpreter.
 * */
class PyUnpinnedCallable(
    val signature: List<PythonType>,
    val module: String?,
    val tag: String,
    val reference: (PyNamespace) -> PyObject, // returns function object reference
) : PyCallable() {
    val numberOfArguments: Int = signature.size

    companion object {
        fun constructCallableFromName(signature: List<PythonType>, name: String, module: String? = null) =
            PyUnpinnedCallable(
                signature,
                module,
                "$module.$name"
            ) { globals -> ConcretePythonInterpreter.eval(globals, name) }

        fun constructLambdaFunction(signature: List<PythonType>, expr: String) =
            PyUnpinnedCallable(
                signature,
                null,
                "lambda \"$expr\""
            ) { globals -> ConcretePythonInterpreter.eval(globals, expr) }
    }
}

/**
 * [TypeMethod] describes type slots.
 * [See docs](https://docs.python.org/3/c-api/typeobj.html).
 * */
sealed class TypeMethod(val isMethodWithNonVirtualReturn: Boolean) : PyCallable()

data object NbBoolMethod : TypeMethod(true)
data object NbIntMethod : TypeMethod(true)
data object NbAddMethod : TypeMethod(false)
data object NbSubtractMethod : TypeMethod(false)
data object NbMultiplyMethod : TypeMethod(false)
data object NbMatrixMultiplyMethod : TypeMethod(false)
data object NbNegativeMethod : TypeMethod(false)
data object NbPositiveMethod : TypeMethod(false)
data object SqConcatMethod : TypeMethod(false)
data object SqLengthMethod : TypeMethod(true)
data object MpSubscriptMethod : TypeMethod(false)
data object MpAssSubscriptMethod : TypeMethod(false)
data class TpRichcmpMethod(val op: Int) : TypeMethod(false)
data object TpGetattro : TypeMethod(false)
data object TpSetattro : TypeMethod(false)
data object TpIterMethod : TypeMethod(false)
data object TpCallMethod : TypeMethod(false)
