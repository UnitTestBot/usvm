package org.usvm.machine.expr

import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsLocal
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
) {
    val runtimeType = scope.calcOnState { arrayStorageType(instance, local.type) }
    if (runtimeType !is EtsClassType && runtimeType !is EtsUnclearRefType) return

    val owners = hierarchy.classesForType(runtimeType)
    val requiresPrototypeMember = owners.any { owner ->
        val hasDataField = owner.getAllFields(hierarchy).any { !it.isStatic && it.hasStringName(fieldName) }
        !hasDataField && hierarchy.nearestInstanceMethod(owner, methodName = fieldName) != null
    }
    if (requiresPrototypeMember) {
        throw UnsupportedOperationException(
            "Declared class member '$fieldName' needs accessor or method-value semantics",
        )
    }
}
