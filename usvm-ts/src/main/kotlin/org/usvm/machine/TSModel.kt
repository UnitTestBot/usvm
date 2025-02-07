package org.usvm.machine

import org.jacodb.ets.base.EtsType
import org.usvm.UAddressSort
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UMockEvaluator
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.machine.expr.TSNullRefExpr
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.UReadOnlyMemoryRegion
import org.usvm.memory.UReadOnlyRegistersStack
import org.usvm.model.UModelBase
import org.usvm.model.UTypeModel

class TSModel(
    ctx: TSContext,
    stack: UReadOnlyRegistersStack,
    types: UTypeModel<EtsType>,
    mocker: UMockEvaluator,
    regions: Map<UMemoryRegionId<*, *>, UReadOnlyMemoryRegion<*, *>>,
    nullRef: UConcreteHeapRef,
    ownership: MutabilityOwnership = MutabilityOwnership(),
) : UModelBase<EtsType>(ctx, stack, types, mocker, regions, nullRef, ownership) {
    val tsNullRef: UConcreteHeapRef = ctx.mkConcreteHeapRef(-1)
}