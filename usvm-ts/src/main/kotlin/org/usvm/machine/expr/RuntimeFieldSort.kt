package org.usvm.machine.expr

import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsField
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.api.typeStreamOf
import org.usvm.isAllocatedConcreteHeapRef
import org.usvm.machine.TsContext
import org.usvm.machine.interpreter.TsStepScope
import org.usvm.types.singleOrNull
import org.usvm.util.EtsHierarchy

internal fun TsContext.resolveAmbiguousFieldSort(
    scope: TsStepScope,
    instance: UHeapRef,
    fields: List<EtsField>,
    hierarchy: EtsHierarchy,
): USort {
    // The allocated class determines storage even when a union or any-typed local
    // (including a computed property access) leaves the frontend field ambiguous.
    if (!isAllocatedConcreteHeapRef(instance)) {
        return unresolvedSort
    }

    val runtimeType = scope.calcOnState { memory.typeStreamOf(instance).singleOrNull() }
    val runtimeClassType = runtimeType as? EtsClassType ?: return unresolvedSort
    val runtimeClass = scene.projectAndSdkClasses.singleOrNull { it.signature == runtimeClassType.signature }
        ?: return unresolvedSort

    val ancestors = hierarchy.getAncestors(runtimeClass)
    // Scene-wide resolution repeats a declaration for every class inheriting it.
    val runtimeFields = fields.filter { it.declaringClass in ancestors }.distinct()
    val runtimeField = runtimeFields.singleOrNull() ?: return unresolvedSort
    return typeToSort(runtimeField.type)
}
