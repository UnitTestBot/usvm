package org.usvm.api.targets

import io.ksmt.utils.asExpr
import org.jacodb.configuration.AnyArgument
import org.jacodb.configuration.Argument
import org.jacodb.configuration.Position
import org.jacodb.configuration.PositionResolver
import org.jacodb.configuration.Result
import org.jacodb.configuration.ThisArgument
import org.usvm.UBoolExpr
import org.usvm.UBv32Sort
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.machine.JcContext


class CallPositionResolver(
    val ctx: JcContext,
    val instance: UHeapRef?,
    val args: List<UExpr<*>>,
    val result: UExpr<*>?,
    // TODO remove nullability
) : PositionResolver<ResolvedPosition<*>?> {
    override fun resolve(position: Position): ResolvedPosition<*>? = when (position) {
        ThisArgument -> instance?.let { mkResolvedPosition(position, it) }

        is Argument -> {
            val index = position.number
            args.getOrNull(index)?.let { mkResolvedPosition(position, it) }
        }

        Result -> result?.let { mkResolvedPosition(position, it) }
        AnyArgument -> error("Unexpected position")
    }

    private fun mkResolvedPosition(position: Position, resolved: UExpr<*>): ResolvedPosition<*>? =
        with(ctx) {
            when (resolved.sort) {
                addressSort -> ResolvedRefPosition(position, resolved.asExpr(addressSort))
                boolSort -> ResolvedBoolPosition(position, resolved.asExpr(boolSort))
                integerSort -> ResolvedIntPosition(position, resolved.asExpr(integerSort))
                else -> null
            }
        }
}

sealed class ResolvedPosition<T>(val position: Position, val resolved: T)

class ResolvedRefPosition(position: Position, resolved: UHeapRef) :
    ResolvedPosition<UHeapRef>(position, resolved)

class ResolvedBoolPosition(position: Position, resolved: UBoolExpr) :
    ResolvedPosition<UBoolExpr>(position, resolved)

class ResolvedIntPosition(position: Position, resolved: UExpr<UBv32Sort>) :
    ResolvedPosition<UExpr<UBv32Sort>>(position, resolved)
