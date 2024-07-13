package org.usvm.machine.symbolicobjects.memory

import io.ksmt.expr.KInterpretedValue
import io.ksmt.sort.KBoolSort
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.api.readField
import org.usvm.language.PyCallable
import org.usvm.machine.ConcolicRunContext
import org.usvm.machine.PyContext
import org.usvm.machine.extractCurState
import org.usvm.machine.symbolicobjects.BoolContents
import org.usvm.machine.symbolicobjects.InterpretedAllocatedOrStaticSymbolicPythonObject
import org.usvm.machine.symbolicobjects.InterpretedInputSymbolicPythonObject
import org.usvm.machine.symbolicobjects.InterpretedSymbolicPythonObject
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject
import org.usvm.machine.types.ConcretePythonType
import org.usvm.machine.types.HasMpLength
import org.usvm.machine.types.HasNbBool
import org.usvm.machine.types.HasSqLength
import org.usvm.machine.types.PythonType
import org.usvm.memory.UMemory

fun UninterpretedSymbolicPythonObject.getBoolContent(ctx: ConcolicRunContext): UExpr<KBoolSort> {
    requireNotNull(ctx.curState)
    addSupertype(ctx, typeSystem.pythonBool)
    return ctx.extractCurState().memory.readField(address, BoolContents.content, BoolContents.content.sort(ctx.ctx))
}

fun UninterpretedSymbolicPythonObject.getToBoolValue(ctx: ConcolicRunContext): UBoolExpr? = with(ctx.ctx) {
    requireNotNull(ctx.curState)
    return when (val type = getTypeIfDefined(ctx)) {
        typeSystem.pythonBool -> {
            getBoolContent(ctx)
        }
        typeSystem.pythonInt -> {
            getIntContent(ctx) neq mkIntNum(0)
        }
        typeSystem.pythonList, typeSystem.pythonTuple -> {
            readArrayLength(ctx) gt mkIntNum(0)
        }
        typeSystem.pythonNoneType -> {
            falseExpr
        }
        typeSystem.pythonDict -> {
            dictIsEmpty(ctx).not()
        }
        typeSystem.pythonSet -> {
            setIsEmpty(ctx).not()
        }
        is ConcretePythonType -> {
            if (HasNbBool.accepts(type) && !HasSqLength.accepts(type) && HasMpLength.accepts(type)) {
                trueExpr
            } else {
                null
            }
        }
        else -> {
            null
        }
    }
}

fun InterpretedInputSymbolicPythonObject.getBoolContent(ctx: PyContext): UBoolExpr {
    require(getConcreteType() == typeSystem.pythonBool)
    return modelHolder.model.readField(address, BoolContents.content, BoolContents.content.sort(ctx))
}

fun InterpretedSymbolicPythonObject.getBoolContent(ctx: PyContext, memory: UMemory<PythonType, PyCallable>): UBoolExpr {
    return when (this) {
        is InterpretedInputSymbolicPythonObject -> {
            getBoolContent(ctx)
        }
        is InterpretedAllocatedOrStaticSymbolicPythonObject -> {
            memory.readField(
                address,
                BoolContents.content,
                BoolContents.content.sort(ctx)
            ) as KInterpretedValue<KBoolSort>
        }
    }
}

fun InterpretedSymbolicPythonObject.getBoolContent(ctx: ConcolicRunContext): UBoolExpr {
    requireNotNull(ctx.curState)
    return getBoolContent(ctx.ctx, ctx.extractCurState().memory)
}
