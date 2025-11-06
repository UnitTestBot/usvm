package org.usvm.api.reachability

import org.jacodb.ets.model.EtsStmt
import org.usvm.api.TsTarget

sealed class TsReachabilityTarget(
    override val location: EtsStmt,
    val id: String? = null,
) : TsTarget(location) {
    override fun toString(): String = location.toString()

    class InitialPoint(location: EtsStmt, id: String? = null) : TsReachabilityTarget(location, id)
    class IntermediatePoint(location: EtsStmt, id: String? = null) : TsReachabilityTarget(location, id)
    class FinalPoint(location: EtsStmt, id: String? = null) : TsReachabilityTarget(location, id)
}
