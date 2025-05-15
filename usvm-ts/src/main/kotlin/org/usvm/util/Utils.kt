package org.usvm.util

import io.ksmt.sort.KFp64Sort
import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUnclearRefType
import org.usvm.UBoolSort
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.machine.TsContext
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState

// Built-in KContext.bvToBool has identical implementation.
fun TsContext.boolToFp(expr: UExpr<UBoolSort>): UExpr<KFp64Sort> =
    mkIte(expr, mkFp64(1.0), mkFp64(0.0))

fun TsState.throwExceptionWithoutStackFrameDrop(address: UHeapRef, type: EtsType) {
    methodResult = TsMethodResult.TsException(address, type)
}

val EtsClass.type: EtsClassType
    get() = EtsClassType(signature, typeParameters)

val EtsMethod.humanReadableSignature: String
    get() {
        val params = parameters.joinToString(",") { it.type.toString() }
        return "${signature.enclosingClass.name}::$name($params):$returnType"
    }

fun EtsType.isResolved(): Boolean =
    this !is EtsUnclearRefType && (this as? EtsClassType)?.signature?.file != EtsFileSignature.UNKNOWN

fun EtsType.getClassesForType(
    scene: EtsScene
): List<EtsClass> = if (isResolved()) {
    scene
        .projectAndSdkClasses
        .filter { it.type == this }
} else {
    val name = typeName.removeTrashFromTheName()
    scene
        .projectAndSdkClasses
        .filter { it.type.typeName.removeTrashFromTheName() == name }
}