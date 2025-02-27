package org.usvm.util

import io.ksmt.sort.KFp64Sort
import org.jacodb.ets.base.EtsType
import org.usvm.UBoolExpr
import org.usvm.UBoolSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.api.typeStreamOf
import org.usvm.machine.TsContext
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.machine.types.FakeType
import org.usvm.types.UTypeStream
import org.usvm.types.single

// Built-in KContext.bvToBool has identical implementation.
fun TsContext.boolToFp(expr: UExpr<UBoolSort>): UExpr<KFp64Sort> =
    mkIte(expr, mkFp64(1.0), mkFp64(0.0))

fun TsState.throwExceptionWithoutStackFrameDrop(address: UHeapRef, type: EtsType) {
    methodResult = TsMethodResult.TsException(address, type)
}

fun TsContext.typeStreamOf(ref: UHeapRef, scope: TsStepScope): UTypeStream<EtsType> =
    scope.calcOnState {
        memory.typeStreamOf(ref)
    }

fun TsContext.getFakeType(ref: UConcreteHeapRef, scope: TsStepScope): FakeType =
    typeStreamOf(ref, scope).single() as FakeType

fun TsContext.extractBool(expr: UConcreteHeapRef, scope: TsStepScope): UBoolExpr {
    val lValue = getIntermediateBoolLValue(expr.address)
    return scope.calcOnState { memory.read(lValue) }
}

fun TsContext.extractFp(expr: UConcreteHeapRef, scope: TsStepScope): UExpr<KFp64Sort> {
    val lValue = getIntermediateFpLValue(expr.address)
    return scope.calcOnState { memory.read(lValue) }
}

fun TsContext.extractRef(expr: UConcreteHeapRef, scope: TsStepScope): UHeapRef {
    val lValue = getIntermediateRefLValue(expr.address)
    return scope.calcOnState { memory.read(lValue) }
}
