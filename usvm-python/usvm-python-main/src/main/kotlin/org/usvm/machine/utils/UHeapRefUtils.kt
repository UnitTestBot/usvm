package org.usvm.machine.utils

import org.usvm.UAddressSort
import org.usvm.UConcreteHeapRef
import org.usvm.UHeapRef
import org.usvm.UIteExpr
import org.usvm.UNullRef
import org.usvm.USymbolicHeapRef
import org.usvm.api.typeStreamOf
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.isTrue
import org.usvm.machine.model.PyModel
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.machine.symbolicobjects.interpretSymbolicPythonObject
import org.usvm.machine.types.PythonType
import org.usvm.types.TypesResult
import org.usvm.types.UTypeStream

fun getLeafHeapRef(ref: UHeapRef, model: PyModel): UHeapRef =
    when (ref) {
        is UConcreteHeapRef -> ref
        is UNullRef -> ref
        is USymbolicHeapRef -> ref
        is UIteExpr<UAddressSort> ->
            if (model.eval(ref.condition).isTrue) {
                getLeafHeapRef(ref.trueBranch, model)
            } else {
                getLeafHeapRef(ref.falseBranch, model)
            }

        else -> error("Unexpected ref: $ref")
    }


fun getTypeStreamForDelayedFork(obj: UninterpretedSymbolicPythonObject, ctx: ConcolicRunContext): UTypeStream<PythonType> {
    require(ctx.curState != null)
    val interpreted = interpretSymbolicPythonObject(ctx, obj)
    if (interpreted.address.address != 0) {
        val current = interpreted.getTypeStream()!!
        val prefix = current.take(3)
        if (prefix is TypesResult.SuccessfulTypesResult && prefix.types.size >= 3) {
            return current
        }
    }
    val leaf = getLeafHeapRef(obj.address, ctx.modelHolder.model)
    return ctx.curState!!.memory.typeStreamOf(leaf)
}
