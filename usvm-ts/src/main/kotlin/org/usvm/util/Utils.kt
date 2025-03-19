package org.usvm.util

import io.ksmt.sort.KFp64Sort
import org.usvm.UBoolSort
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.machine.TsContext
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.model.TsClass
import org.usvm.model.TsClassType
import org.usvm.model.TsMethod
import org.usvm.model.TsType

// Built-in KContext.bvToBool has identical implementation.
fun TsContext.boolToFp(expr: UExpr<UBoolSort>): UExpr<KFp64Sort> =
    mkIte(expr, mkFp64(1.0), mkFp64(0.0))

fun TsState.throwExceptionWithoutStackFrameDrop(address: UHeapRef, type: TsType) {
    methodResult = TsMethodResult.TsException(address, type)
}

val TsClass.type: TsClassType
    get() = TsClassType(signature, typeParameters)

val TsMethod.humanReadableSignature: String
    get() {
        val params = parameters.joinToString(",") { it.type.toString() }
        return "${signature.enclosingClass.name}::$name($params):$returnType"
    }
