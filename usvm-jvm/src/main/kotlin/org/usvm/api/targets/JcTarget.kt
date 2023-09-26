package org.usvm.api.targets

import org.jacodb.api.cfg.JcInst
import org.usvm.targets.UTarget
import org.usvm.targets.UTargetController

/**
 * Base class for JcMachine targets.
 */
abstract class JcTarget<TargetController : UTargetController>(
    location: JcInst? = null
) : UTarget<JcInst, JcTarget<TargetController>, TargetController>(location)
