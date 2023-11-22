package org.usvm.machine.interpreters.operations.basic

import org.usvm.interpreter.ConcolicRunContext
import org.usvm.language.types.HasTpHash
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.machine.symbolicobjects.dictContainsRef
import org.usvm.machine.symbolicobjects.readDictRefElement

fun handlerDictGetItemKt(
    ctx: ConcolicRunContext,
    dict: UninterpretedSymbolicPythonObject,
    key: UninterpretedSymbolicPythonObject
): UninterpretedSymbolicPythonObject? {
    ctx.curState ?: return null
    key.addSupertypeSoft(ctx, HasTpHash)
    val keyType = key.getTypeIfDefined(ctx)
    val typeSystem = ctx.typeSystem
    return when (keyType) {
        typeSystem.pythonInt,
        typeSystem.pythonFloat,
        typeSystem.pythonBool,
        typeSystem.pythonNoneType -> null
        null -> {
            val clonedState = ctx.curState!!.clone()
            val stateForDelayedFork =
                myAssertOnState(clonedState, ctx.ctx.mkNot(ctx.ctx.mkHeapRefEq(key.address, ctx.ctx.nullRef)))
            stateForDelayedFork?.let { addDelayedFork(ctx, key, it) }
            null
        }
        else -> {
            myFork(ctx, dict.dictContainsRef(ctx, key))
            dict.readDictRefElement(ctx, key)
        }
    }
}