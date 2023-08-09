package org.usvm.machine.symbolicobjects

import io.ksmt.expr.KInt32NumExpr
import org.usvm.*
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.machine.utils.PyModelHolder
import org.usvm.machine.interpreters.operations.myAssert
import org.usvm.language.types.pythonList

class ObjectValidator(private val concolicRunContext: ConcolicRunContext) {
    private val checked = mutableSetOf<UConcreteHeapRef>()
    fun check(symbol: UninterpretedSymbolicPythonObject) {
        val modelHolder = concolicRunContext.modelHolder
        val concrete = interpretSymbolicPythonObject(symbol, modelHolder)
        if (checked.contains(concrete.address))
            return
        checked.add(concrete.address)
        when (concrete.getConcreteType(concolicRunContext)) {
            //pythonInt -> checkInt(symbol)
            //pythonNoneType -> checkNone(symbol)
            //pythonBool -> checkBool(symbol)
            pythonList -> checkList(symbol, modelHolder)
        }
    }

    /*
    private fun checkInt(symbolic: UninterpretedSymbolicPythonObject) = with(concolicRunContext.ctx) {
        val cond = (symbolic.getIntContent(concolicRunContext) eq mkIntNum(0)) xor symbolic.getBoolContent(concolicRunContext)
        myAssert(concolicRunContext, cond)
    }

    private fun checkNone(symbolic: UninterpretedSymbolicPythonObject) = with(concolicRunContext.ctx) {
        myAssert(concolicRunContext, symbolic.getBoolContent(concolicRunContext).not())
    }

    private fun checkBool(symbolic: UninterpretedSymbolicPythonObject) = with(concolicRunContext.ctx) {
        val isTrue = symbolic.getBoolContent(concolicRunContext)
        val isFalse = isTrue.not()
        val asInt = symbolic.getIntContent(concolicRunContext)
        myAssert(concolicRunContext, isTrue implies (asInt eq mkIntNum(1)))
        myAssert(concolicRunContext, isFalse implies (asInt eq mkIntNum(0)))
    }*/

    @Suppress("unchecked_parameter")
    private fun checkList(symbolic: UninterpretedSymbolicPythonObject, modelHolder: PyModelHolder) = with(concolicRunContext.ctx) {
        require(concolicRunContext.curState != null)
        @Suppress("unchecked_cast")
        val symbolicSize = concolicRunContext.curState!!.memory.read(UArrayLengthLValue(symbolic.address, pythonList)) as USizeExpr
        myAssert(concolicRunContext, symbolicSize ge mkIntNum(0))
        val size = modelHolder.model.eval(symbolicSize) as KInt32NumExpr
        List(size.value) { index ->
            @Suppress("unchecked_cast")
            val element = concolicRunContext.curState!!.memory.read(UArrayIndexLValue(addressSort, symbolic.address, mkSizeExpr(index), pythonList)) as UHeapRef
            val elemObj = UninterpretedSymbolicPythonObject(element)
            check(elemObj)
        }
    }
}