package org.usvm.util

import io.ksmt.sort.KFp64Sort
import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUnclearRefType
import org.usvm.UBoolSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.machine.TsContext
import org.usvm.machine.expr.tctx
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.machine.types.mkFakeValue

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

// TODO save info about this field somewhere
//      https://github.com/UnitTestBot/usvm/issues/288
fun UHeapRef.createFakeField(fieldName: String, scope: TsStepScope): UConcreteHeapRef {
    val ctx = this.tctx

    val lValue = mkFieldLValue(ctx.addressSort, this, fieldName)

    val boolLValue = mkFieldLValue(ctx.boolSort, this, fieldName)
    val fpLValue = mkFieldLValue(ctx.fp64Sort, this, fieldName)
    val refLValue = mkFieldLValue(ctx.addressSort, this, fieldName)

    val boolValue = scope.calcOnState { memory.read(boolLValue) }
    val fpValue = scope.calcOnState { memory.read(fpLValue) }
    val refValue = scope.calcOnState { memory.read(refLValue) }

    val fakeObject = ctx.mkFakeValue(scope, boolValue, fpValue, refValue)
    scope.doWithState {
        memory.write(lValue, fakeObject.asExpr(ctx.addressSort), guard = ctx.trueExpr)
    }

    return fakeObject
}