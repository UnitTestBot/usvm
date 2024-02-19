package org.usvm.machine.utils

import org.usvm.*
import org.usvm.api.typeStreamOf
import org.usvm.constraints.UTypeConstraints
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.language.types.MockType
import org.usvm.language.types.PythonType
import org.usvm.machine.model.PyModel
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.machine.symbolicobjects.interpretSymbolicPythonObject
import org.usvm.types.TypesResult
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
    val interpreted = interpretSymbolicPythonObject(ctx, obj)
    if (interpreted.address.address != 0)
        return interpreted.getTypeStream()!!
    val leaf = getLeafHeapRef(obj.address, ctx.curState!!.pyModel)
    return ctx.curState!!.memory.typeStreamOf(leaf)
}

private fun isMockTypeStream(stream: UTypeStream<PythonType>): Boolean {
    val prefix = stream.take(2)
    if (prefix !is TypesResult.SuccessfulTypesResult)
        return false
    if (prefix.types.size != 1)
        return false
    return prefix.types.first() is MockType
}

fun heapRefIsMocked(ref: UHeapRef, typeConstraints: UTypeConstraints<PythonType>): Boolean =
    when (ref) {
        is UNullRef -> true
        is UConcreteHeapRef, is USymbolicHeapRef ->
            isMockTypeStream(typeConstraints.getTypeStream(ref))
        is UIteExpr<UAddressSort> ->
            heapRefIsMocked(ref.trueBranch, typeConstraints) && heapRefIsMocked(ref.falseBranch, typeConstraints)
        else -> error("Unexpected ref: $ref")
    }

fun symbolIsMocked(obj: UninterpretedSymbolicPythonObject, ctx: ConcolicRunContext): Boolean {
    val state = ctx.curState
    require(state != null)
    return heapRefIsMocked(obj.address, state.pathConstraints.typeConstraints)
}