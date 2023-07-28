package org.usvm.interpreter.symbolicobjects

import org.usvm.interpreter.ConcolicRunContext

class ObjectValidator(private val concolicRunContext: ConcolicRunContext) {
    @Suppress("unused_parameter")
    fun check(symbol: UninterpretedSymbolicPythonObject) {
        /*val model = concolicRunContext.curState.pyModel
        val concrete = interpretSymbolicPythonObject(symbol, model)
        with(concolicRunContext.ctx) {
            myAssert(concolicRunContext, mkHeapRefEq(symbol.address, nullRef).not())
        }
        when (concrete.getConcreteType()) {
            pythonInt -> checkInt(symbol)
            pythonNoneType -> checkNone(symbol)
            pythonBool -> checkBool(symbol)
            pythonList -> checkList(symbol, concrete)
        }*/
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
    }

    private fun checkList(symbolic: UninterpretedSymbolicPythonObject, concrete: InterpretedSymbolicPythonObject) = with(concolicRunContext.ctx) {
        @Suppress("unchecked_cast")
        val symbolicSize = concolicRunContext.curState.memory.read(UArrayLengthLValue(symbolic.address, pythonList)) as USizeExpr
        myAssert(concolicRunContext, symbolicSize ge mkIntNum(0))
        val size = concrete.model.eval(symbolicSize) as KInt32NumExpr
        myAssert(concolicRunContext, symbolicSize le mkIntNum(10))  // temporary
        List(size.value) { index ->
            @Suppress("unchecked_cast")
            val element = concolicRunContext.curState.memory.read(UArrayIndexLValue(addressSort, symbolic.address, mkSizeExpr(index), pythonList)) as UHeapRef
            myAssert(concolicRunContext, (symbolicSize gt mkIntNum(index)) implies mkNot(mkHeapRefEq(element, nullRef)))
            val elemObj = UninterpretedSymbolicPythonObject(element)
            if (concolicRunContext.curState.useOnlyIntsAsInternalObjects)
                elemObj.addSupertype(concolicRunContext, pythonInt)
            check(elemObj)
        }
    }
     */
}