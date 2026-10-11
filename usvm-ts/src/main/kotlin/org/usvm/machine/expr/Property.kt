package org.usvm.machine.expr

import io.ksmt.utils.asExpr
import org.jacodb.ets.model.EtsArrayAccess
import org.jacodb.ets.model.EtsArrayType
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsDefineDataPropertyStmt
import org.jacodb.ets.model.EtsFieldSignature
import org.jacodb.ets.model.EtsInstanceFieldRef
import org.jacodb.ets.model.EtsLValue
import org.jacodb.ets.model.EtsNumberConstant
import org.jacodb.ets.model.EtsPropertyRef
import org.jacodb.ets.model.EtsStringConstant
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.util.arrayStorageType

internal fun TsExprResolver.handlePropertyRef(value: EtsPropertyRef): UExpr<*>? {
    val normalized = normalizePropertyRef(value) ?: return null

    return when (normalized) {
        is EtsArrayAccess -> handleArrayAccess(normalized)
        is EtsInstanceFieldRef -> handleInstanceFieldRef(normalized)
        else -> error("Unexpected normalized property: $normalized")
    }
}

internal fun TsExprResolver.handleAssignToProperty(value: EtsPropertyRef, expr: UExpr<*>): Unit? {
    val normalized = normalizePropertyRef(value) ?: return null

    return when (normalized) {
        is EtsArrayAccess -> handleAssignToArrayIndex(normalized, expr)
        is EtsInstanceFieldRef -> handleAssignToInstanceField(normalized, expr)
        else -> error("Unexpected normalized property: $normalized")
    }
}

/** Reuses the existing model for concrete data-property names and array indices. */
private fun TsExprResolver.normalizePropertyRef(value: EtsPropertyRef): EtsLValue? = with(ctx) {
    var key = resolve(value.key) ?: return null
    if (key.isFakeObject()) {
        scope.assert(key.getFakeType(scope).refTypeExpr) ?: return null
        key = key.extractRef(scope)
    }
    val name = (key as? UConcreteHeapRef)?.let(::getStringConstantValue)
        ?: throw UnsupportedOperationException("Property access needs a constant converted key: ${value.key}")

    val receiver = resolve(value.instance) ?: return null
    val reference = receiver.asExpr(addressSort).unwrapRefWithPathConstraint(scope)
    val storageType = scope.calcOnState { arrayStorageType(reference.asExpr(addressSort), value.instance.type) }
    if (storageType is EtsArrayType) {
        val index = name.toIntOrNull()?.takeIf { it >= 0 && it.toString() == name }
            ?: throw UnsupportedOperationException("Unsupported array property: $name")
        return EtsArrayAccess(array = value.instance, index = EtsNumberConstant(index.toDouble()), type = value.type)
    }

    val owner = (storageType as? EtsClassType)?.signature
        ?: throw UnsupportedOperationException("Property access needs a modeled object: ${value.instance}")
    val field = EtsFieldSignature(enclosingClass = owner, name = name, type = value.type)
    EtsInstanceFieldRef(instance = value.instance, field = field, type = value.type)
}

internal fun TsExprResolver.handleDefineDataProperty(stmt: EtsDefineDataPropertyStmt): Unit? {
    val key = stmt.key as? EtsStringConstant
        ?: throw UnsupportedOperationException("Data property requires a constant string key: ${stmt.key}")
    val owner = (stmt.target.type as? EtsClassType)?.signature
        ?: throw UnsupportedOperationException("Data property requires a modeled object: ${stmt.target}")
    val value = resolve(stmt.value) ?: return null
    val field = EtsFieldSignature(enclosingClass = owner, name = key.value, type = stmt.value.type)
    val target = EtsInstanceFieldRef(instance = stmt.target, field = field, type = stmt.value.type)
    return handleAssignToInstanceField(target, value)
}
