package org.usvm.interpreter

import org.usvm.UContext
import org.usvm.interpreter.symbolicobjects.InterpretedSymbolicPythonObject
import org.usvm.language.types.pythonInt
import org.usvm.language.types.pythonBool

class ConverterToPythonObject(private val ctx: UContext) {
    fun convert(obj: InterpretedSymbolicPythonObject): PythonObject? =
        when (obj.getConcreteType()) {
            pythonInt -> convertInt(obj)
            pythonBool -> convertBool(obj)
            else -> TODO()
        }

    private fun convertInt(obj: InterpretedSymbolicPythonObject): PythonObject =
        ConcretePythonInterpreter.eval(emptyNamespace, obj.getIntContent(ctx).toString())

    private fun convertBool(obj: InterpretedSymbolicPythonObject): PythonObject? =
        when (obj.getBoolContent(ctx)) {
            ctx.trueExpr -> ConcretePythonInterpreter.eval(emptyNamespace, "True")
            ctx.falseExpr -> ConcretePythonInterpreter.eval(emptyNamespace, "False")
            else -> null
        }
}