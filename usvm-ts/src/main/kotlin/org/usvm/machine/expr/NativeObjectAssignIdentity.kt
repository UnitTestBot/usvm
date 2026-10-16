package org.usvm.machine.expr

import org.jacodb.ets.model.EtsInstanceFieldRef
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsUnknownType
import org.usvm.UConcreteHeapRef
import org.usvm.isFalse
import org.usvm.machine.TsContext
import org.usvm.machine.runtimeFunctionType

/** Object.assign ignores its this argument, so an extracted native function keeps the same call boundary. */
internal fun TsExprResolver.tryCaptureNativeObjectAssign(field: EtsInstanceFieldRef): TsExprApproximationResult {
    if (field.field.name != "assign") return TsExprApproximationResult.NoApproximation
    val receiver = resolve(field.instance) ?: return TsExprApproximationResult.ResolveFailure
    if (receiver !is UConcreteHeapRef) return TsExprApproximationResult.NoApproximation
    if (!ctx.isNativeObjectConstructor(receiver)) return TsExprApproximationResult.NoApproximation

    val changed = scope.calcOnState {
        val written = memory.read(ctx.writtenPropertyLValue(receiver, field.field.name))
        val deleted = memory.read(ctx.deletedFieldLValue(receiver, field.field))
        !written.isFalse || !deleted.isFalse
    }
    if (changed) {
        val stored = ctx.resolveField(scope, field.instance, receiver, field.field, hierarchy)
            ?: return TsExprApproximationResult.ResolveFailure
        return TsExprApproximationResult.SuccessfulApproximation(stored)
    }

    val owner = requireNotNull(ctx.classConstructorSignature(receiver))
    val prior = scope.calcOnState {
        capturedInstanceCalls.firstOrNull { (_, captured) ->
            val native = captured.nativeCallee
            native != null && native.enclosingClass == owner && native.name == field.field.name
        }?.key
    }
    if (prior != null) return TsExprApproximationResult.SuccessfulApproximation(prior)

    val callee = EtsMethodSignature(
        enclosingClass = owner,
        name = field.field.name,
        parameters = emptyList(),
        returnType = EtsUnknownType,
    )
    val pointer = scope.calcOnState { memory.allocConcrete(ctx.scene.runtimeFunctionType()) }
    scope.doWithState {
        capturedInstanceCalls = capturedInstanceCalls.put(
            pointer,
            TsCapturedInstanceCall(
                receiver = receiver,
                sourceReceiver = field.instance,
                nativeCallee = callee,
            ),
            ownership,
        )
    }
    return TsExprApproximationResult.SuccessfulApproximation(pointer)
}

private fun TsContext.isNativeObjectConstructor(instance: UConcreteHeapRef): Boolean {
    val signature = classConstructorSignature(instance) ?: return false
    return scene.sdkClasses.any { clazz -> clazz.name == "Object" && clazz.signature == signature }
}
