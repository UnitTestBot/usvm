package org.usvm.machine.expr

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsFieldSignature
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.machine.TsContext
import org.usvm.machine.interpreter.TsStepScope

/** A stopped intrinsic operation must never fall through to ordinary object storage. */
internal sealed interface TsIntrinsicFieldResult<out T> {
    data object NotApplicable : TsIntrinsicFieldResult<Nothing>
    data class Handled<T>(val value: T) : TsIntrinsicFieldResult<T>
    data object Stopped : TsIntrinsicFieldResult<Nothing>

    companion object {
        fun <T : Any> from(value: T?): TsIntrinsicFieldResult<T> = when (value) {
            null -> Stopped
            else -> Handled(value)
        }
    }
}

internal fun TsContext.tryReadIntrinsicField(
    scope: TsStepScope,
    instance: UHeapRef,
    field: EtsFieldSignature,
): TsIntrinsicFieldResult<UExpr<*>> {
    val regularExpression = intrinsicRegExpReference(scope, instance)
    if (regularExpression != null) {
        return TsIntrinsicFieldResult.from(readRegExpField(scope, regularExpression, field.name))
    }

    if (isDateModelTimestamp(scope = scope, instance = instance, field = field)) {
        val timestamp = dateModelTimestampLValue(instance)
        val value = scope.calcOnState { memory.read(timestamp) }
        return TsIntrinsicFieldResult.Handled(value)
    }

    // Date.constructor implements own-property deletion and inherited lookup itself.
    val dateConstructor = readIntrinsicDateConstructor(scope, instance, field.name)
    if (dateConstructor != null) return TsIntrinsicFieldResult.Handled(dateConstructor)

    return TsIntrinsicFieldResult.NotApplicable
}

internal fun TsContext.tryWriteIntrinsicField(
    scope: TsStepScope,
    instance: UHeapRef,
    field: EtsFieldSignature,
    value: UExpr<*>,
): TsIntrinsicFieldResult<Unit> {
    val regularExpression = intrinsicRegExpReference(scope, instance)
    if (regularExpression != null) {
        return TsIntrinsicFieldResult.from(writeRegExpField(scope, regularExpression, field.name, value))
    }

    if (isDateModelTimestamp(scope = scope, instance = instance, field = field)) {
        val timestamp = dateModelTimestampLValue(instance)
        scope.doWithState {
            memory.write(timestamp, value.asExpr(fp64Sort), guard = trueExpr)
        }
        return TsIntrinsicFieldResult.Handled(Unit)
    }

    return TsIntrinsicFieldResult.NotApplicable
}

private fun TsContext.intrinsicRegExpReference(scope: TsStepScope, instance: UHeapRef): UConcreteHeapRef? {
    val reference = instance as? UConcreteHeapRef ?: return null
    val isRegExp = scope.calcOnState { reference in regularExpressions }
    return if (isRegExp) reference else null
}
