package org.usvm.machine.expr

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsInstanceCallExpr
import org.jacodb.ets.utils.CONSTRUCTOR_NAME
import org.jacodb.ets.utils.callExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.api.typeStreamOf
import org.usvm.isAllocatedConcreteHeapRef
import org.usvm.machine.TsContext
import org.usvm.machine.call.TsUnknownCall
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.machine.state.TsState
import org.usvm.types.singleOrNull
import org.usvm.util.EtsHierarchy
import org.usvm.util.mkFieldLValue

internal val intrinsicDateInstanceType = EtsClassType(EtsClassSignature.UNKNOWN.copy(name = "Date"))
internal val intrinsicDateConstructorSignature = EtsHierarchy.OBJECT_CLASS.signature.copy(name = "Date")

internal fun TsContext.isIntrinsicDateConstructorProperty(
    scope: TsStepScope,
    instance: UHeapRef,
    name: String,
): Boolean {
    if (name != CONSTRUCTOR_NAME || !isAllocatedConcreteHeapRef(instance)) return false
    val type = scope.calcOnState { memory.typeStreamOf(instance).singleOrNull() }
    return type == intrinsicDateInstanceType
}

internal fun TsContext.readIntrinsicDateConstructor(
    scope: TsStepScope,
    instance: UHeapRef,
    name: String,
): UExpr<*>? {
    if (!isIntrinsicDateConstructorProperty(scope, instance, name)) return null

    val hasPrototypeMutation = scope.calcOnState { (instance to PROTOTYPE_PROPERTY_NAME) in writtenConcreteFields }
    if (hasPrototypeMutation) {
        throw UnsupportedOperationException("Date constructor lookup after prototype mutation is not modeled")
    }

    val constructor = classConstructorRef(intrinsicDateConstructorSignature)
    val wasWritten = scope.calcOnState { (instance to name) in writtenConcreteFields }
    if (!wasWritten) return constructor

    val ownConstructor = mkFieldLValue(addressSort, instance, name)
    val deleted = deletedFieldLValue(instance, name)
    return scope.calcOnState {
        ctx.mkIte(memory.read(deleted), constructor, memory.read(ownConstructor))
    }
}

internal fun TsState.intrinsicDateConstructorReceiver(call: TsUnknownCall): UHeapRef? {
    if (call.callee.name != CONSTRUCTOR_NAME || call.callSite.callExpr !is EtsInstanceCallExpr) return null
    val resolvedReceiver = call.receiver?.resolved ?: return null
    if (resolvedReceiver.sort != ctx.addressSort) return null

    val receiver = resolvedReceiver.asExpr(ctx.addressSort)
    if (!isAllocatedConcreteHeapRef(receiver)) return null

    val type = memory.typeStreamOf(receiver).singleOrNull()
    return if (type == intrinsicDateInstanceType) receiver else null
}

/** The intrinsic constructor value is known, but its mutable prototype object is not represented. */
internal fun TsContext.ensureSupportedDatePrototypeAccess(instance: UHeapRef, name: String) {
    val constructor = instance as? UConcreteHeapRef ?: return
    val isDatePrototype = name == "prototype" &&
        classConstructorSignature(constructor) == intrinsicDateConstructorSignature
    if (isDatePrototype) {
        throw UnsupportedOperationException("Date.prototype access is not modeled")
    }
}
