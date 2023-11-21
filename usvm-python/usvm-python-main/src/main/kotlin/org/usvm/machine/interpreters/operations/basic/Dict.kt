package org.usvm.machine.interpreters.operations.basic

import org.usvm.interpreter.ConcolicRunContext
import org.usvm.language.types.HasTpHash
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.machine.symbolicobjects.readDictElement

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
        null,
        typeSystem.pythonInt,
        typeSystem.pythonFloat,
        typeSystem.pythonBool,
        typeSystem.pythonNoneType -> null
        else -> {
            dict.readDictElement(ctx, key)
        }
    }
}