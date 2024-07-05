package org.usvm.machine.interpreters.symbolic.operations.nativecalls

import org.usvm.annotations.ids.NativeId
import org.usvm.machine.ConcolicRunContext
import org.usvm.machine.interpreters.concrete.PyObject
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject

fun addConstraintsFromNativeId(
    ctx: ConcolicRunContext,
    function: PyObject,
    args: List<UninterpretedSymbolicPythonObject>,
) {
    constraintHolder.forEach {
        if (function.address == it.id.cRef) {
            it.apply(ctx, args)
        }
    }
}

abstract class NativeCallConstraint(val id: NativeId) {
    abstract fun apply(ctx: ConcolicRunContext, args: List<UninterpretedSymbolicPythonObject>)
}
