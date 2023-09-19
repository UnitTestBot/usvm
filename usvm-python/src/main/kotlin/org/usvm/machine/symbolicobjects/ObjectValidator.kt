package org.usvm.machine.symbolicobjects

import io.ksmt.expr.KInt32NumExpr
import org.usvm.*
import org.usvm.api.readArrayIndex
import org.usvm.api.readArrayLength
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.language.types.ArrayType
import org.usvm.language.types.MockType
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
            typeSystem.pythonTuple -> checkTuple(symbol, modelHolder)
            else -> Unit
        }
    }

    private fun checkList(symbolic: UninterpretedSymbolicPythonObject, modelHolder: PyModelHolder) = with(concolicRunContext.ctx) {
        require(concolicRunContext.curState != null)
        val symbolicSize = concolicRunContext.curState!!.memory.readArrayLength(symbolic.address, ArrayType)
        myAssert(concolicRunContext, mkAnd(symbolicSize ge mkIntNum(0), symbolicSize le mkIntNum(1000_000)))
        val size = modelHolder.model.eval(symbolicSize) as KInt32NumExpr
        List(size.value) { index ->
            val element = concolicRunContext.curState!!.memory.readArrayIndex(
                symbolic.address,
                mkSizeExpr(index),
                ArrayType,
                addressSort
            )
            val elemObj = UninterpretedSymbolicPythonObject(element, typeSystem)
            check(elemObj)
        }
    }

    private fun checkTuple(symbolic: UninterpretedSymbolicPythonObject, modelHolder: PyModelHolder) = with(concolicRunContext.ctx) {
        require(concolicRunContext.curState != null)
        val time = symbolic.getTimeOfCreation(concolicRunContext)
        val symbolicSize = concolicRunContext.curState!!.memory.readArrayLength(symbolic.address, ArrayType)
        myAssert(concolicRunContext, symbolicSize ge mkIntNum(0))
        val size = modelHolder.model.eval(symbolicSize) as KInt32NumExpr
        for (index in 0 until size.value) {
            val element = concolicRunContext.curState!!.memory.readArrayIndex(symbolic.address, mkSizeExpr(index), ArrayType, addressSort)
            val elemObj = UninterpretedSymbolicPythonObject(element, typeSystem)
            val interpretedElem = interpretSymbolicPythonObject(elemObj, modelHolder)
            if (interpretedElem.getFirstType(concolicRunContext) !is MockType) {
                val elemTime = elemObj.getTimeOfCreation(concolicRunContext)
                val concreteTime = modelHolder.model.eval(time).toString().toInt()
                val concreteElemTime = modelHolder.model.eval(elemTime).toString().toInt()
                require(concreteTime > concreteElemTime)
            }
            check(elemObj)
        }
    }
}