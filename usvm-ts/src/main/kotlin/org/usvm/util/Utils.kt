package org.usvm.util

import io.ksmt.sort.KFp64Sort
import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsClass
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUnclearRefType
import org.usvm.UBoolSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.api.TsTarget
import org.usvm.api.mockMethodCall
import org.usvm.machine.TsContext
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState
import org.usvm.machine.types.mkFakeValue

// Built-in KContext.bvToBool has identical implementation.
fun TsContext.boolToFp(expr: UExpr<UBoolSort>): UExpr<KFp64Sort> =
    mkIte(expr, mkFp64(1.0), mkFp64(0.0))

fun TsState.throwExceptionWithoutStackFrameDrop(ref: UHeapRef, type: EtsType) {
    methodResult = TsMethodResult.TsException(ref, type)
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
    scene: EtsScene,
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
fun TsContext.createFakeField(
    scope: TsStepScope,
    instance: UHeapRef,
    fieldName: String,
): UConcreteHeapRef {
    val lValue = mkFieldLValue(addressSort, instance, fieldName)

    val boolLValue = mkFieldLValue(boolSort, instance, fieldName)
    val fpLValue = mkFieldLValue(fp64Sort, instance, fieldName)
    val refLValue = mkFieldLValue(addressSort, instance, fieldName)

    val bool = scope.calcOnState { memory.read(boolLValue) }
    val fp = scope.calcOnState { memory.read(fpLValue) }
    val ref = scope.calcOnState { memory.read(refLValue) }

    if (ref.isFakeObject()) {
        return ref
    }

    return scope.calcOnState {
        val fakeObject = mkFakeValue(scope, bool, fp, ref)
        memory.write(lValue, fakeObject.asExpr(ctx.addressSort), guard = ctx.trueExpr)
        lValuesToAllocatedFakeObjects += refLValue to fakeObject
        fakeObject
    }
}

/**
 * Counts all leaf targets in the tree.
 */
fun TsTarget.countLeaves(): Int = when {
    children.isEmpty() -> 1
    else -> children.sumOf { it.countLeaves() }
}

/**
 * Returns all leaf targets in the tree.
 */
fun TsTarget.getLeaves(): List<TsTarget> = when {
    children.isEmpty() -> listOf(this)
    else -> children.flatMap { it.getLeaves() }
}

fun TsContext.fakeOrDie(scope: TsStepScope, isTargetMode: Boolean): UExpr<*>? =
    if (isTargetMode) {
        // In targeted mode, we cannot under-approximate, so we return a fake value
        scope.calcOnState { mkFakeValue(scope) }
    } else {
        // In normal mode, we kill the state since we cannot proceed
        scope.assert(falseExpr)
        null
    }

fun TsContext.mockOrDie(
    scope: TsStepScope,
    method: EtsMethodSignature,
    isTargetMode: Boolean,
) {
    if (isTargetMode) {
        // In targeted mode, we cannot under-approximate, so we mock the method call
        mockMethodCall(scope, method)
    } else {
        // In normal mode, we kill the state since we cannot proceed
        scope.assert(falseExpr)
    }
}
