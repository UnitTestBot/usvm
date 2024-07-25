package org.usvm.machine

import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentHashSetOf
import org.usvm.UBoolExpr
import org.usvm.UBv32Sort
import org.usvm.UContext
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.constraints.UEqualityConstraints
import org.usvm.constraints.ULogicalConstraints
import org.usvm.constraints.UNumericConstraints
import org.usvm.constraints.UPathConstraints
import org.usvm.constraints.UTypeConstraints
import org.usvm.machine.types.PythonType

class PyPathConstraints(
    ctx: UContext<*>,
    override var ownership: MutabilityOwnership,
    logicalConstraints: ULogicalConstraints = ULogicalConstraints.empty(),
    equalityConstraints: UEqualityConstraints = UEqualityConstraints(ctx, ownership),
    typeConstraints: UTypeConstraints<PythonType> = UTypeConstraints(
        ownership,
        ctx.typeSystem(),
        equalityConstraints
    ),
    numericConstraints: UNumericConstraints<UBv32Sort> =
        UNumericConstraints(ctx, sort = ctx.bv32Sort, ownership = ownership),
    var pythonSoftConstraints: PersistentSet<UBoolExpr> = persistentHashSetOf(),
) : UPathConstraints<PythonType>(
    ctx,
    ownership,
    logicalConstraints,
    equalityConstraints,
    typeConstraints,
    numericConstraints
) {
    override fun clone(thisOwnership: MutabilityOwnership, cloneOwnership: MutabilityOwnership): PyPathConstraints {
        val clonedLogicalConstraints = logicalConstraints.clone()
        val clonedEqualityConstraints = equalityConstraints.clone(thisOwnership, cloneOwnership)
        val clonedTypeConstraints = typeConstraints.clone(clonedEqualityConstraints, thisOwnership, cloneOwnership)
        val clonedNumericConstraints = numericConstraints.clone(thisOwnership, cloneOwnership)
        this.ownership = thisOwnership
        return PyPathConstraints(
            ctx = ctx,
            logicalConstraints = clonedLogicalConstraints,
            equalityConstraints = clonedEqualityConstraints,
            typeConstraints = clonedTypeConstraints,
            numericConstraints = clonedNumericConstraints,
            pythonSoftConstraints = pythonSoftConstraints,
            ownership = cloneOwnership
        )
    }
}
