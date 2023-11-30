package org.usvm.machine.interpreters.operations.basic

import org.usvm.UBoolExpr
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.isFalse
import org.usvm.isTrue
import org.usvm.language.types.ConcreteTypeNegation
import org.usvm.language.types.HasTpHash
import org.usvm.machine.symbolicobjects.*
import java.util.stream.Stream
import kotlin.streams.asSequence

private fun forkOnUnknownType(
    ctx: ConcolicRunContext,
    key: UninterpretedSymbolicPythonObject
) = with(ctx.ctx) {
    require(key.getTypeIfDefined(ctx) == null)
    val keyIsInt = key.evalIs(ctx, ctx.typeSystem.pythonInt)
    val keyIsBool = key.evalIs(ctx, ctx.typeSystem.pythonBool)
    val keyIsFloat = key.evalIs(ctx, ctx.typeSystem.pythonFloat)
    val keyIsNone = key.evalIs(ctx, ctx.typeSystem.pythonNoneType)
    require(ctx.modelHolder.model.eval(keyIsInt or keyIsBool).isFalse)
    myFork(ctx, keyIsInt)
    myFork(ctx, keyIsBool)
    require(ctx.modelHolder.model.eval(keyIsFloat or keyIsNone).isFalse)
    myAssert(ctx, (keyIsFloat or keyIsNone).not())
}

private fun addKeyTypeConstrains(
    ctx: ConcolicRunContext,
    key: UninterpretedSymbolicPythonObject
) = with(ctx.ctx) {
    var cond: UBoolExpr = trueExpr
    cond = cond and key.evalIsSoft(ctx, HasTpHash)
    cond = cond and key.evalIs(ctx, ctx.typeSystem.pythonList).not()
    cond = cond and key.evalIs(ctx, ctx.typeSystem.pythonDict).not()
    cond = cond and key.evalIs(ctx, ctx.typeSystem.pythonSet).not()
    myAssert(ctx, cond)
}

fun handlerDictGetItemKt(
    ctx: ConcolicRunContext,
    dict: UninterpretedSymbolicPythonObject,
    key: UninterpretedSymbolicPythonObject
): UninterpretedSymbolicPythonObject? {
    ctx.curState ?: return null
    addKeyTypeConstrains(ctx, key)
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
        else -> {
            if (keyType == null) {
                forkOnUnknownType(ctx, key)
            }
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

private fun setItem(
    ctx: ConcolicRunContext,
    dict: UninterpretedSymbolicPythonObject,
    key: UninterpretedSymbolicPythonObject,
    value: UninterpretedSymbolicPythonObject
) {
    val typeSystem = ctx.typeSystem
    when (val keyType = key.getTypeIfDefined(ctx)) {
        typeSystem.pythonFloat, typeSystem.pythonNoneType -> Unit  // TODO
        typeSystem.pythonInt -> {
            val intValue = key.getToIntContent(ctx) ?: return
            dict.writeDictIntElement(ctx, intValue, value)
        }
        else -> {
            if (keyType == null) {
                forkOnUnknownType(ctx, key)
            }
            dict.writeDictRefElement(ctx, key, value)
        }
    }
}

fun handlerDictSetItemKt(
    ctx: ConcolicRunContext,
    dict: UninterpretedSymbolicPythonObject,
    key: UninterpretedSymbolicPythonObject,
    value: UninterpretedSymbolicPythonObject
) {
    ctx.curState ?: return
    addKeyTypeConstrains(ctx, key)
    val typeSystem = ctx.typeSystem
    dict.addSupertypeSoft(ctx, typeSystem.pythonDict)
    setItem(ctx, dict, key, value)
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
        addKeyTypeConstrains(ctx, key)
        setItem(ctx, result, key, elem)
    }
    return result
}

fun handlerCreateDictConstKeyKt(
    ctx: ConcolicRunContext,
    keys: UninterpretedSymbolicPythonObject,
    elemsStream: Stream<UninterpretedSymbolicPythonObject>
): UninterpretedSymbolicPythonObject? {
    ctx.curState ?: return null
    val elems = elemsStream.asSequence().toList()
    val typeSystem = ctx.typeSystem
    keys.addSupertypeSoft(ctx, typeSystem.pythonTuple)
    val ref = ctx.curState!!.memory.allocConcrete(typeSystem.pythonDict)
    val result = UninterpretedSymbolicPythonObject(ref, ctx.typeSystem)
    elems.forEachIndexed { index, elem ->
        val key = keys.readArrayElement(ctx, ctx.ctx.mkIntNum(index))
        addKeyTypeConstrains(ctx, key)
        setItem(ctx, result, key, elem)
    }
    return result
}

fun handlerDictContainsKt(
    ctx: ConcolicRunContext,
    dict: UninterpretedSymbolicPythonObject,
    key: UninterpretedSymbolicPythonObject
) {
    ctx.curState ?: return
    addKeyTypeConstrains(ctx, key)
    val keyType = key.getTypeIfDefined(ctx)
    val typeSystem = ctx.typeSystem
    val result: UBoolExpr = when (keyType) {
        typeSystem.pythonFloat, typeSystem.pythonNoneType -> return  // TODO
        typeSystem.pythonInt, typeSystem.pythonBool -> {
            val intValue = key.getToIntContent(ctx) ?: return
            dict.dictContainsInt(ctx, intValue)
        }
        else -> {
            if (keyType == null) {
                forkOnUnknownType(ctx, key)
            }
            dict.dictContainsRef(ctx, key)
        }
    }
    myFork(ctx, result)
}