package org.usvm.reachability.api

import org.jacodb.ets.model.EtsStmt
import org.usvm.api.TsTarget

sealed class TsReachabilityTarget(override val location: EtsStmt) : TsTarget(location) {
    class InitialPoint(location: EtsStmt) : TsReachabilityTarget(location)
    class IntermediatePoint(location: EtsStmt) : TsReachabilityTarget(location)
    class FinalPoint(location: EtsStmt) : TsReachabilityTarget(location)
}
