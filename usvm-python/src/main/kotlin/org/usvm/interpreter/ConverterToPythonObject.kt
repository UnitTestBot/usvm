package org.usvm.interpreter

import io.ksmt.expr.KIntNumExpr
import org.usvm.UExpr
import org.usvm.language.PythonInt
import org.usvm.language.PythonType

class ConverterToPythonObject(private val namespace: PythonNamespace) {
    fun convert(expr: UExpr<*>, targetType: PythonType): PythonObject? =
        when (targetType) {
            is PythonInt -> convertInt(expr)
        }

    private fun convertInt(expr: UExpr<*>): PythonObject? {
        if (expr !is KIntNumExpr)
            return null
        return ConcretePythonInterpreter.eval(namespace, expr.toString())
    }
}