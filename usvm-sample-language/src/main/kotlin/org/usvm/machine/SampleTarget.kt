package org.usvm.machine

import org.usvm.UTarget
import org.usvm.language.Method
import org.usvm.language.Stmt

/**
 * Base class for SampleMachine targets.
 */
abstract class SampleTarget(location: Stmt) : UTarget<Method<*>, Stmt, SampleTarget, SampleState>(location)
