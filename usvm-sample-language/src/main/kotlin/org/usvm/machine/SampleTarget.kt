package org.usvm.machine

import org.usvm.language.Stmt
import org.usvm.targets.UTarget
import org.usvm.targets.UTargetController

/**
 * Base class for SampleMachine targets.
 */
abstract class SampleTarget<TargetController : UTargetController>(
    location: Stmt,
) : UTarget<Stmt, SampleTarget<TargetController>, TargetController>(location)
