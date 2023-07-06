package org.usvm.interpreter

import org.usvm.UContext
import org.usvm.interpreter.symbolicobjects.InterpretedSymbolicPythonObject
import org.usvm.language.pythonInt
import org.usvm.language.pythonBool

class ConverterToPythonObject(private val ctx: UContext) {
    fun convert(obj: InterpretedSymbolicPythonObject): PythonObject? =
        when (obj.concreteType) {
            pythonInt -> convertInt(obj)
            pythonBool -> convertBool(obj)
            else -> null
        }

    private fun convertInt(obj: InterpretedSymbolicPythonObject): PythonObject =
        ConcretePythonInterpreter.eval(emptyNamespace, obj.getIntContent().toString())

    private fun convertBool(obj: InterpretedSymbolicPythonObject): PythonObject? =
        when (obj.getBoolContent()) {
            ctx.trueExpr -> ConcretePythonInterpreter.eval(emptyNamespace, "True")
            ctx.falseExpr -> ConcretePythonInterpreter.eval(emptyNamespace, "False")
            else -> null
        }
}