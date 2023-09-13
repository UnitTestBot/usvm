package org.usvm.api.targets

import org.jacodb.api.cfg.JcInst
import org.usvm.UTarget
import org.usvm.machine.state.JcState

/**
 * Base class for JcMachine targets.
 */
abstract class JcTarget(
    location: JcInst? = null
) : UTarget<JcInst, JcTarget, JcState>(location)
