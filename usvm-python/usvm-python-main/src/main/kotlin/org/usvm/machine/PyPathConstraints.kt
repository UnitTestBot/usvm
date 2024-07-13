package org.usvm.machine

import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentHashSetOf
import org.usvm.UBoolExpr
import org.usvm.UBv32Sort
import org.usvm.UContext
import org.usvm.constraints.UEqualityConstraints
import org.usvm.constraints.ULogicalConstraints
import org.usvm.constraints.UNumericConstraints
import org.usvm.constraints.UPathConstraints
import org.usvm.constraints.UTypeConstraints
import org.usvm.machine.types.PythonType

class PyPathConstraints(
    ctx: UContext<*>,
    logicalConstraints: ULogicalConstraints = ULogicalConstraints.empty(),
    equalityConstraints: UEqualityConstraints = UEqualityConstraints(ctx),
    typeConstraints: UTypeConstraints<PythonType> = UTypeConstraints(
        ctx.typeSystem(),
        equalityConstraints
    ),
    numericConstraints: UNumericConstraints<UBv32Sort> = UNumericConstraints(ctx, sort = ctx.bv32Sort),
    var pythonSoftConstraints: PersistentSet<UBoolExpr> = persistentHashSetOf(),
) : UPathConstraints<PythonType>(
    ctx,
    logicalConstraints,
    equalityConstraints,
    typeConstraints,
    numericConstraints
) {
    override fun clone(): PyPathConstraints {
        val clonedLogicalConstraints = logicalConstraints.clone()
        val clonedEqualityConstraints = equalityConstraints.clone()
        val clonedTypeConstraints = typeConstraints.clone(clonedEqualityConstraints)
        val clonedNumericConstraints = numericConstraints.clone()
        return PyPathConstraints(
            ctx = ctx,
            logicalConstraints = clonedLogicalConstraints,
            equalityConstraints = clonedEqualityConstraints,
            typeConstraints = clonedTypeConstraints,
            numericConstraints = clonedNumericConstraints,
            pythonSoftConstraints = pythonSoftConstraints,
        )
    }
}
