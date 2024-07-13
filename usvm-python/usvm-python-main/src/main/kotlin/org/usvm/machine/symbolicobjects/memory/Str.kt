package org.usvm.machine.symbolicobjects.memory

import org.usvm.machine.symbolicobjects.PreallocatedObjects
import org.usvm.machine.symbolicobjects.UninterpretedSymbolicPythonObject

fun UninterpretedSymbolicPythonObject.getConcreteStrIfDefined(preallocatedObjects: PreallocatedObjects): String? =
    preallocatedObjects.concreteString(this)
