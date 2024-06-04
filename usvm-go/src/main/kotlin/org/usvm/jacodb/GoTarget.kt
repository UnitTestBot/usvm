package org.usvm.jacodb

import org.jacodb.go.api.GoInst
import org.usvm.targets.UTarget

abstract class GoTarget(
    location: GoInst,
) : UTarget<GoInst, GoTarget>(location)