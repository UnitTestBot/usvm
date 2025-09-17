package org.usvm.api

import org.jacodb.ets.model.EtsStmt
import org.usvm.targets.UTarget

open class TsTarget(location: EtsStmt?) : UTarget<EtsStmt, TsTarget>(location)
