package org.usvm.api.targets

import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcInst
import org.usvm.Location
import org.usvm.UTarget
import org.usvm.machine.state.JcState

/**
 * Base class for JcMachine targets.
 */
abstract class JcTarget(
    location: Location<JcMethod, JcInst>? = null
) : UTarget<JcMethod, JcInst, JcTarget, JcState>(location)
