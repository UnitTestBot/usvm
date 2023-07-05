package org.usvm.interpreter

import io.ksmt.expr.KIntNumExpr
import org.usvm.interpreter.symbolicobjects.InterpretedSymbolicPythonObject
import org.usvm.language.pythonInt
import org.usvm.language.PythonType

class ConverterToPythonObject(private val namespace: PythonNamespace) {
    fun convert(obj: InterpretedSymbolicPythonObject, targetType: PythonType): PythonObject? =
        when (targetType) {
            pythonInt -> convertInt(obj)
            else -> null
        }

    private fun convertInt(obj: InterpretedSymbolicPythonObject): PythonObject? {
        if (obj.getIntContent() !is KIntNumExpr)
            return null
        return ConcretePythonInterpreter.eval(namespace, obj.getIntContent().toString())
    }
}