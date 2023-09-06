package org.usvm.machine

import org.usvm.Location
import org.usvm.UTarget
import org.usvm.language.Method
import org.usvm.language.Stmt

/**
 * Base class for SampleMachine targets.
 */
abstract class SampleTarget(location: Location<Method<*>, Stmt>) : UTarget<Method<*>, Stmt, SampleTarget, SampleState>(location)
