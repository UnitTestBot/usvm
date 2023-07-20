package org.usvm.interpreter.symbolicobjects

import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.interpreter.ConcretePythonInterpreter
import org.usvm.interpreter.PythonObject
import org.usvm.interpreter.emptyNamespace
import org.usvm.language.SymbolForCPython
import org.usvm.language.VirtualPythonObject
import org.usvm.language.types.pythonInt
import org.usvm.language.types.pythonBool

class ConverterToPythonObject(private val ctx: UContext) {
    private val constructedObjects = mutableMapOf<UConcreteHeapRef, PythonObject>()
    private val virtualObjects = mutableMapOf<UConcreteHeapRef, Pair<VirtualPythonObject, PythonObject>>()
    fun restart() {
        constructedObjects.clear()
        virtualObjects.clear()
    }

    fun getVirtualObjects(): Collection<PythonObject> = virtualObjects.values.map { it.second }

    fun convert(
        obj: InterpretedSymbolicPythonObject,
        symbol: SymbolForCPython? = null,
        //concolicRunContext: ConcolicRunContext? = null
    ): PythonObject {
        val cached = constructedObjects[obj.address]
        if (cached != null)
            return cached
        val result = when (obj.getConcreteType()) {
            null -> constructVirtualObject(
                obj,
                symbol ?: error("Symbol must not be null if virtual object creation is possible")
            )
            pythonInt -> convertInt(obj)
            pythonBool -> convertBool(obj)
            else -> TODO()
        }
        constructedObjects[obj.address] = result
        return result
    }

    private fun constructVirtualObject(obj: InterpretedSymbolicPythonObject, symbol: SymbolForCPython): PythonObject {
        val virtual = VirtualPythonObject(obj, symbol)
        val result = ConcretePythonInterpreter.allocateVirtualObject(virtual)
        virtualObjects[obj.address] = virtual to result
        return result
    }

    private fun convertInt(obj: InterpretedSymbolicPythonObject): PythonObject =
        ConcretePythonInterpreter.eval(emptyNamespace, obj.getIntContent(ctx).toString())

    private fun convertBool(obj: InterpretedSymbolicPythonObject): PythonObject =
        when (obj.getBoolContent(ctx)) {
            ctx.trueExpr -> ConcretePythonInterpreter.eval(emptyNamespace, "True")
            ctx.falseExpr -> ConcretePythonInterpreter.eval(emptyNamespace, "False")
            else -> error("Not reachable")
        }
}