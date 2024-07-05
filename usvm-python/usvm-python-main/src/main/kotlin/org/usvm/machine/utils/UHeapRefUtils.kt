package org.usvm.machine.utils

import org.usvm.UAddressSort
import org.usvm.UConcreteHeapRef
import org.usvm.UHeapRef
import org.usvm.UIteExpr
import org.usvm.UNullRef
import org.usvm.USymbolicHeapRef
import org.usvm.api.typeStreamOf
import org.usvm.isTrue
import org.usvm.machine.ConcolicRunContext
import org.usvm.machine.extractCurState
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

private const val PREFIX_SIZE = 3

fun getTypeStreamForDelayedFork(
    obj: UninterpretedSymbolicPythonObject,
    ctx: ConcolicRunContext,
): UTypeStream<PythonType> {
    requireNotNull(ctx.curState)
    val interpreted = interpretSymbolicPythonObject(ctx, obj)
    if (interpreted.address.address != 0) {
        val current = interpreted.getTypeStream() ?: error("getTypeStream() should not be null here")
        val prefix = current.take(PREFIX_SIZE)
        if (prefix is TypesResult.SuccessfulTypesResult && prefix.types.size >= PREFIX_SIZE) {
            return current
        }
    }
    val leaf = getLeafHeapRef(obj.address, ctx.modelHolder.model)
    return ctx.extractCurState().memory.typeStreamOf(leaf)
}
