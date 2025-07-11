package org.usvm.api.targets

import org.jacodb.ets.model.EtsStmt
import org.usvm.targets.UTarget

open class TsTarget(location: EtsStmt?) : UTarget<EtsStmt, TsTarget>(location)

sealed class TsReachabilityTarget(override val location: EtsStmt) : TsTarget(location) {
    class InitialPoint(location: EtsStmt) : TsReachabilityTarget(location)
    class IntermediatePoint(location: EtsStmt) : TsReachabilityTarget(location)
    class FinalPoint(location: EtsStmt) : TsReachabilityTarget(location)
}
