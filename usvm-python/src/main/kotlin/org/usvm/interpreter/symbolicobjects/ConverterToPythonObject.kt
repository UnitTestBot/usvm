package org.usvm.interpreter.symbolicobjects

import io.ksmt.expr.KInt32NumExpr
import org.usvm.UArrayIndexLValue
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UHeapRef
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.interpreter.ConcretePythonInterpreter
import org.usvm.interpreter.PythonObject
import org.usvm.interpreter.emptyNamespace
import org.usvm.language.SymbolForCPython
import org.usvm.language.VirtualPythonObject
import org.usvm.language.types.*
import org.usvm.types.UTypeStream

class ConverterToPythonObject(private val ctx: UContext) {
    private val constructedObjects = mutableMapOf<UConcreteHeapRef, PythonObject>()
    private val virtualObjects = mutableMapOf<SymbolForCPython, Pair<VirtualPythonObject, PythonObject>>()
    fun restart() {
        constructedObjects.clear()
        virtualObjects.clear()
    }

    fun getVirtualObjects(): Collection<PythonObject> = virtualObjects.values.map { it.second }
    fun getSymbolsWithoutConcreteTypes(): Collection<SymbolForCPython> = virtualObjects.keys

    fun convert(
        obj: InterpretedSymbolicPythonObject,
        symbol: SymbolForCPython? = null,
        concolicRunContext: ConcolicRunContext? = null
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
            pythonObjectType -> ConcretePythonInterpreter.eval(emptyNamespace, "object()")
            pythonNoneType -> ConcretePythonInterpreter.eval(emptyNamespace, "None")
            pythonList -> convertList(obj, symbol?.obj, concolicRunContext)
            else -> TODO()
        }
        constructedObjects[obj.address] = result
        return result
    }

    private fun constructVirtualObject(obj: InterpretedSymbolicPythonObject, symbol: SymbolForCPython): PythonObject {
        val virtual = VirtualPythonObject(obj, symbol)
        val result = ConcretePythonInterpreter.allocateVirtualObject(virtual)
        virtualObjects[symbol] = virtual to result
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

    private fun convertList(obj: InterpretedSymbolicPythonObject, symbol: UninterpretedSymbolicPythonObject?, concolicRunContext: ConcolicRunContext?): PythonObject = with(ctx) {
        val size = obj.model.uModel.heap.readArrayLength(obj.address, pythonList) as KInt32NumExpr
        val listOfPythonObjects = List(size.value) { index ->
            val indexExpr = mkSizeExpr(index)
            val element = obj.model.uModel.heap.readArrayIndex(obj.address, indexExpr, pythonList, addressSort) as UConcreteHeapRef
            val elemInterpretedObject = InterpretedSymbolicPythonObject(element, obj.model)
            val elementSymbolicAddress = symbol?.address?.let { address ->
                concolicRunContext?.curState?.memory?.read(UArrayIndexLValue(addressSort, address, indexExpr, pythonList))
            }
            val elementSymbol = elementSymbolicAddress?.let {
                @Suppress("unchecked_cast")
                SymbolForCPython(UninterpretedSymbolicPythonObject(it as UHeapRef))
            }
            convert(elemInterpretedObject, elementSymbol, concolicRunContext)
        }
        return ConcretePythonInterpreter.makeList(listOfPythonObjects)
    }
}