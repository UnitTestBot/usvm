package org.usvm.machine.expr

import org.jacodb.ets.model.EtsBooleanType
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsNumberType
import org.jacodb.ets.model.EtsStringType
import org.jacodb.ets.model.EtsUnclearRefType
import org.usvm.UHeapRef
import org.usvm.machine.TsContext
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.util.EtsHierarchy
import org.usvm.util.arrayStorageType
import org.usvm.util.getAllFields
import org.usvm.util.hasStringName

/** EtsIR currently represents accessors and ordinary prototype methods with the same method metadata. */
internal fun TsContext.ensureSupportedDeclaredMemberAccess(
    scope: TsStepScope,
    instance: UHeapRef,
    local: EtsLocal,
    fieldName: String,
    hierarchy: EtsHierarchy,
    isWrite: Boolean = false,
) {
    val runtimeType = scope.calcOnState { arrayStorageType(instance, local.type) }
    if (runtimeType !is EtsClassType && runtimeType !is EtsUnclearRefType) return

    val owners = hierarchy.classesForType(runtimeType)
    val requiresPrototypeMember = owners.any { owner ->
        val hasDataField = owner.getAllFields(hierarchy).any { !it.isStatic && it.hasStringName(fieldName) }
        val method = hierarchy.nearestInstanceMethod(owner, methodName = fieldName)
        val writesOrdinaryMethod = isWrite && method?.hasOrdinaryMethodSignature() == true
        !hasDataField && method != null && !writesOrdinaryMethod
    }
    if (requiresPrototypeMember) {
        throw UnsupportedOperationException(
            "Declared class member '$fieldName' needs accessor or method-value semantics",
        )
    }
}

private fun EtsMethod.hasOrdinaryMethodSignature(): Boolean {
    // A TypeScript getter has no parameters; a setter has one parameter and returns void.
    val returnsPrimitiveValue = returnType == EtsBooleanType || returnType == EtsNumberType ||
        returnType == EtsStringType
    return when (parameters.size) {
        0 -> false
        1 -> returnsPrimitiveValue
        else -> true
    }
}
