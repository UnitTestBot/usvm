package org.usvm.machine.expr

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUnknownType
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.UIteExpr
import org.usvm.isFalse
import org.usvm.machine.TsVirtualMethodCallStmt
import org.usvm.util.arrayStorageType

/** Missing own array slots may yield undefined only while prototype changes stay outside the supported domain. */
internal fun TsExprResolver.ensureSupportedArrayPrototypeAccess(receiver: UHeapRef, fieldName: String): Boolean {
    if (receiver is UIteExpr<*>) {
        val resolved = ctx.resolveHeapRef(scope, receiver) ?: return false
        return ensureSupportedArrayPrototypeAccess(resolved, fieldName)
    }

    if (fieldName == "__proto__") {
        val runtimeType = scope.calcOnState { arrayStorageType(receiver, EtsUnknownType) }
        if (runtimeType is EtsArrayType) {
            throw UnsupportedOperationException("Array prototype access and mutation are not modeled")
        }
    }
    if (fieldName != "prototype" || receiver !is UConcreteHeapRef) return true

    val signature = ctx.classConstructorSignature(receiver) ?: return true
    val coreConstructor = ctx.scene.sdkClasses.any { clazz ->
        clazz.signature == signature && clazz.name in setOf("Object", "Array")
    }
    if (coreConstructor) {
        throw UnsupportedOperationException("Builtin Object and Array prototype access is not modeled")
    }
    return true
}

internal fun TsExprResolver.ensureSupportedObjectMutation(stmt: TsVirtualMethodCallStmt): Boolean = with(ctx) {
    val changesOrExposesProperties = stmt.call.callee.name in setOf(
        "setPrototypeOf", "getPrototypeOf", "defineProperty", "defineProperties", "assign",
        "freeze", "seal", "preventExtensions",
    )
    if (!changesOrExposesProperties) return true

    val receiver = stmt.instance as? UConcreteHeapRef ?: return true
    val signature = ctx.classConstructorSignature(receiver) ?: return true
    val builtinObject = ctx.scene.sdkClasses.any { clazz -> clazz.name == "Object" && clazz.signature == signature }
    if (!builtinObject) return true

    if (stmt.call.callee.name == "getPrototypeOf") {
        // Even an ordinary object can expose Object.prototype, which supplies inherited array indexes.
        throw UnsupportedOperationException("Builtin prototype reflection is not modeled")
    }

    val target = stmt.args.firstOrNull() ?: return true
    val staticType = stmt.call.args.firstOrNull()?.type ?: EtsUnknownType
    val targetsArray = arrayTargetCondition(target, staticType)
    if (targetsArray.isFalse) return true

    // Ordinary object targets retain their existing dispatcher/model behavior.
    scope.fork(mkNot(targetsArray), blockOnFalseState = {
        terminateAsUnsupported(reason = "Reflective Object operations on arrays are not modeled")
    }) != null
}

private fun TsExprResolver.arrayTargetCondition(target: UExpr<*>, staticType: EtsType): UBoolExpr = with(ctx) {
    if (target.sort != addressSort) return falseExpr
    if (target.isFakeObject()) {
        val referenceKind = target.getFakeType(scope).refTypeExpr
        val reference = target.extractRef(scope)
        return mkAnd(referenceKind, arrayTargetCondition(reference, staticType))
    }
    if (target is UIteExpr<*>) {
        return mkIte(
            target.condition,
            arrayTargetCondition(target.trueBranch, staticType),
            arrayTargetCondition(target.falseBranch, staticType),
        )
    }

    val reference = target.asExpr(addressSort)
    val runtimeType = scope.calcOnState { arrayStorageType(reference, staticType) }
    mkBool(runtimeType is EtsArrayType)
}
