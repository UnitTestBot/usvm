package org.usvm.machine.call

import org.jacodb.ets.model.EtsAnyType
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFunctionType
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUnclearRefType
import org.jacodb.ets.model.EtsUnknownType
import org.jacodb.ets.model.EtsValue

internal fun hasBuiltinGlobalOwner(
    owner: EtsValue,
    callee: EtsMethodSignature,
    expectedName: String,
): Boolean = owner.type.isBuiltinGlobalType(expectedName) ||
    owner.isLegacyBuiltinGlobal(expectedName = expectedName, callee = callee)

internal fun EtsType.isBuiltinGlobalType(expectedName: String): Boolean = when (expectedName) {
    "Math" -> this is EtsUnclearRefType && name == expectedName
    "Number" -> this is EtsFunctionType && isBuiltinNumberType()
    else -> false
}

private fun EtsFunctionType.isBuiltinNumberType(): Boolean {
    val parameter = signature.parameters.singleOrNull() ?: return false
    return signature.enclosingClass == EtsClassSignature.UNKNOWN &&
        signature.name.isEmpty() &&
        signature.returnType == EtsNumberType &&
        parameter.type == EtsAnyType &&
        parameter.isOptional &&
        !parameter.isRest
}

private fun EtsValue.isLegacyBuiltinGlobal(
    expectedName: String,
    callee: EtsMethodSignature,
): Boolean = this is EtsLocal &&
    name == expectedName &&
    type == EtsUnknownType &&
    callee.enclosingClass == EtsClassSignature.UNKNOWN.copy(name = expectedName)
