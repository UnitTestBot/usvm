package org.usvm.machine.symbolicobjects.memory

import io.ksmt.expr.KInterpretedValue
import io.ksmt.sort.KBoolSort
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.api.readField
import org.usvm.interpreter.ConcolicRunContext
import org.usvm.language.PythonCallable
import org.usvm.language.types.*
import org.usvm.machine.PyContext
import org.usvm.machine.symbolicobjects.*
import org.usvm.memory.UMemory


fun UninterpretedSymbolicPythonObject.getBoolContent(ctx: ConcolicRunContext): UExpr<KBoolSort> {
    require(ctx.curState != null)
    addSupertype(ctx, typeSystem.pythonBool)
    return ctx.curState!!.memory.readField(address, BoolContents.content, ctx.ctx.boolSort)
}

fun UninterpretedSymbolicPythonObject.getToBoolValue(ctx: ConcolicRunContext): UBoolExpr? = with (ctx.ctx) {
    require(ctx.curState != null)
    return when (val type = getTypeIfDefined(ctx)) {
        typeSystem.pythonBool -> getBoolContent(ctx)
        typeSystem.pythonInt -> getIntContent(ctx) neq mkIntNum(0)
        typeSystem.pythonList, typeSystem.pythonTuple -> readArrayLength(ctx) gt mkIntNum(0)
        typeSystem.pythonNoneType -> falseExpr
        typeSystem.pythonDict -> dictIsEmpty(ctx).not()
        typeSystem.pythonSet -> setIsEmpty(ctx).not()
        is ConcretePythonType -> {
            if (HasNbBool.accepts(type) && !HasSqLength.accepts(type) && HasMpLength.accepts(type))
                trueExpr
            else
                null
        }
        else -> null
    }
}

fun InterpretedInputSymbolicPythonObject.getBoolContent(ctx: PyContext): UBoolExpr {
    require(getConcreteType() == typeSystem.pythonBool)
    return modelHolder.model.readField(address, BoolContents.content, ctx.boolSort)
}

fun InterpretedSymbolicPythonObject.getBoolContent(ctx: PyContext, memory: UMemory<PythonType, PythonCallable>): UBoolExpr {
    return when (this) {
        is InterpretedInputSymbolicPythonObject -> {
            getBoolContent(ctx)
        }
        is InterpretedAllocatedOrStaticSymbolicPythonObject -> {
            memory.readField(address, BoolContents.content, ctx.boolSort) as KInterpretedValue<KBoolSort>
        }
    }
}

fun InterpretedSymbolicPythonObject.getBoolContent(ctx: ConcolicRunContext): UBoolExpr {
    require(ctx.curState != null)
    return getBoolContent(ctx.ctx, ctx.curState!!.memory)
}