package org.usvm.machine.symbolicobjects

import io.ksmt.expr.KInt32NumExpr
import org.usvm.*
import org.usvm.api.readArrayIndex
import org.usvm.api.readArrayLength
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.language.types.ArrayType
import org.usvm.machine.utils.PyModelHolder
import org.usvm.machine.interpreters.operations.myAssert

class ObjectValidator(private val concolicRunContext: ConcolicRunContext) {
    private val checked = mutableSetOf<UConcreteHeapRef>()
    private val typeSystem = concolicRunContext.typeSystem
    fun check(symbol: UninterpretedSymbolicPythonObject) {
        val modelHolder = concolicRunContext.modelHolder
        val concrete = interpretSymbolicPythonObject(symbol, modelHolder)
        if (checked.contains(concrete.address))
            return
        checked.add(concrete.address)
        when (concrete.getConcreteType(concolicRunContext)) {
            typeSystem.pythonList -> checkList(symbol, modelHolder)
        }
    }

    private fun checkList(symbolic: UninterpretedSymbolicPythonObject, modelHolder: PyModelHolder) = with(concolicRunContext.ctx) {
        require(concolicRunContext.curState != null)
        val arrayType = ArrayType(concolicRunContext.typeSystem)
        val symbolicSize = concolicRunContext.curState!!.memory.readArrayLength(symbolic.address, arrayType)
        myAssert(concolicRunContext, symbolicSize ge mkIntNum(0))
        val size = modelHolder.model.eval(symbolicSize) as KInt32NumExpr
        List(size.value) { index ->
            val element = concolicRunContext.curState!!.memory.readArrayIndex(
                symbolic.address,
                mkSizeExpr(index),
                arrayType,
                addressSort
            )
            val elemObj = UninterpretedSymbolicPythonObject(element, typeSystem)
            check(elemObj)
        }
    }
}