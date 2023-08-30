package org.usvm.machine.utils

import org.usvm.*
import org.usvm.api.typeStreamOf
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.language.types.PythonType
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.machine.symbolicobjects.interpretSymbolicPythonObject
import org.usvm.types.UTypeStream

fun getLeafHeapRef(ref: UHeapRef, model: PyModel): UHeapRef =
    when (ref) {
        is UConcreteHeapRef -> ref
        is UNullRef -> ref
        is USymbolicHeapRef -> ref
        is UIteExpr<UAddressSort> ->
            if (model.eval(ref.condition).isTrue)
                getLeafHeapRef(ref.trueBranch, model)
            else
                getLeafHeapRef(ref.falseBranch, model)

        else -> error("Unexpected ref: $ref")
    }


fun getTypeStreamForDelayedFork(obj: UninterpretedSymbolicPythonObject, ctx: ConcolicRunContext): UTypeStream<PythonType> {
    require(ctx.curState != null)
    val interpreted = interpretSymbolicPythonObject(obj, ctx.modelHolder)
    if (interpreted.address.address != 0)
        return interpreted.getTypeStream(ctx)!!
    val leaf = getLeafHeapRef(obj.address, ctx.curState!!.pyModel)
    return ctx.curState!!.memory.typeStreamOf(leaf)
}