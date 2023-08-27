package org.usvm.machine

import org.usvm.UTarget
import org.usvm.language.Method
import org.usvm.language.Stmt

open class SampleTarget(method: Method<*>, statement: Stmt) : UTarget<Method<*>, Stmt, SampleTarget, SampleState>(method, statement)
