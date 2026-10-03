package org.usvm.machine.expr

import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsField
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsUnionType
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
    instanceLocal: EtsLocal?,
    instance: UHeapRef,
    fields: List<EtsField>,
    hierarchy: EtsHierarchy,
): USort {
    // A dynamic constructor leaves the local union-typed after allocating one concrete class.
    // Keep the existing representation for other ambiguous and any-typed fields.
    if (instanceLocal?.type !is EtsUnionType || !isAllocatedConcreteHeapRef(instance)) {
        return unresolvedSort
    }

    val runtimeType = scope.calcOnState { memory.typeStreamOf(instance).singleOrNull() }
    val runtimeClass = (runtimeType as? EtsClassType)?.let { type ->
        scene.projectAndSdkClasses.singleOrNull { it.signature == type.signature }
    } ?: return unresolvedSort

    val ancestors = hierarchy.getAncestors(runtimeClass)
    val runtimeField = fields.singleOrNull { it.declaringClass in ancestors } ?: return unresolvedSort
    return typeToSort(runtimeField.type)
}
