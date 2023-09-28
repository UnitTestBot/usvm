package org.usvm.machine

import org.usvm.language.Stmt
import org.usvm.targets.UTarget

/**
 * Base class for SampleMachine targets.
 */
abstract class SampleTarget(
    location: Stmt,
) : UTarget<Stmt, SampleTarget>(location)
