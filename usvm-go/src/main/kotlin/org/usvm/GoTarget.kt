package org.usvm

import org.jacodb.go.api.GoInst
import org.usvm.targets.UTarget

open class GoTarget(
    location: GoInst,
) : UTarget<GoInst, GoTarget>(location)
