package org.usvm.api.targets

import org.jacodb.api.jvm.cfg.JcInst
import org.usvm.targets.UTarget

/**
 * Base class for JcMachine targets.
 */
abstract class JcTarget(location: JcInst? = null) : UTarget<JcInst, JcTarget>(location)
