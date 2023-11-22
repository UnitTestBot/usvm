package org.usvm.machine.interpreters.operations.basic

import org.usvm.interpreter.ConcolicRunContext
import org.usvm.isTrue
import org.usvm.language.types.HasTpHash
import org.usvm.machine.symbolicobjects.*
import java.util.stream.Stream
import kotlin.streams.asSequence

private fun forkOnUnknownType(
    ctx: ConcolicRunContext,
    key: UninterpretedSymbolicPythonObject
) {
    val clonedState = ctx.curState!!.clone()
    val stateForDelayedFork =
        myAssertOnState(clonedState, ctx.ctx.mkNot(ctx.ctx.mkHeapRefEq(key.address, ctx.ctx.nullRef)))
    stateForDelayedFork?.let { addDelayedFork(ctx, key, it) }
}

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
        typeSystem.pythonFloat, typeSystem.pythonNoneType -> null  // TODO
        typeSystem.pythonInt, typeSystem.pythonBool -> {
            val intValue = key.getToIntContent(ctx) ?: return null
            val containsCond = dict.dictContainsInt(ctx, intValue)
            myFork(ctx, containsCond)
            if (ctx.modelHolder.model.eval(containsCond).isTrue) {
                dict.readDictIntElement(ctx, intValue)
            } else {
                null
            }
        }
        null -> {
            forkOnUnknownType(ctx, key)
            null
        }
        else -> {
            val containsCond = dict.dictContainsRef(ctx, key)
            myFork(ctx, containsCond)
            if (ctx.modelHolder.model.eval(containsCond).isTrue) {
                dict.readDictRefElement(ctx, key)
            } else {
                null
            }
        }
    }
}

fun handlerCreateDictKt(
    ctx: ConcolicRunContext,
    keysStream: Stream<UninterpretedSymbolicPythonObject>,
    elemsStream: Stream<UninterpretedSymbolicPythonObject>
): UninterpretedSymbolicPythonObject? {
    ctx.curState ?: return null
    val keys = keysStream.asSequence().toList()
    val elems = elemsStream.asSequence().toList()
    require(keys.size == elems.size)
    val typeSystem = ctx.typeSystem
    val ref = ctx.curState!!.memory.allocConcrete(typeSystem.pythonDict)
    val result = UninterpretedSymbolicPythonObject(ref, ctx.typeSystem)
    (keys zip elems).forEach { (key, elem) ->
        val keyType = key.getTypeIfDefined(ctx)
        when (keyType) {
            null -> forkOnUnknownType(ctx, key)
            typeSystem.pythonFloat, typeSystem.pythonNoneType -> Unit  // TODO
            typeSystem.pythonInt, typeSystem.pythonBool -> {
                val keyValue = key.getToIntContent(ctx) ?: return@forEach
                result.writeDictIntElement(ctx, keyValue, elem)
            }
            else -> {
                result.writeDictRefElement(ctx, key, elem)
            }
        }
    }
    return result
}