package org.usvm.api.targets

import io.ksmt.utils.asExpr
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.uctx


interface PositionResolver {
    fun resolve(position: Position): ResolvedPosition<*>?
}

class CallPositionResolver(
    val instance: UHeapRef?,
    val args: List<UExpr<*>>,
    val result: UExpr<*>?,
) : PositionResolver {
    override fun resolve(position: Position): ResolvedPosition<*>? = when (position) {
        ThisArgument -> instance?.let { mkResolvedPosition(position, it) }

        is Argument -> {
            val index = position.number.toInt()
            args.getOrNull(index)?.let { mkResolvedPosition(position, it) }
        }

        Result -> result?.let { mkResolvedPosition(position, it) }
    }

    private fun mkResolvedPosition(position: Position, resolved: UExpr<*>): ResolvedPosition<*>? =
        with(resolved.uctx) {
            when (resolved.sort) {
                addressSort -> ResolvedRefPosition(position, resolved.asExpr(addressSort))
                boolSort -> ResolvedBoolPosition(position, resolved.asExpr(boolSort))
                else -> null
            }
        }
}


sealed interface Position

object ThisArgument : Position

class Argument(val number: UInt) : Position

object Result : Position

sealed class ResolvedPosition<T>(val position: Position, val resolved: T)
class ResolvedRefPosition(position: Position, resolved: UHeapRef) : ResolvedPosition<UHeapRef>(position, resolved)
class ResolvedBoolPosition(position: Position, resolved: UBoolExpr) : ResolvedPosition<UBoolExpr>(position, resolved)
