package org.usvm.machine.interpreters.concrete.utils

import org.usvm.machine.symbolicobjects.InterpretedInputSymbolicPythonObject


class VirtualPythonObject(
    @JvmField
    val interpretedObjRef: Int
) {
    companion object {
        fun from(interpretedObj: InterpretedInputSymbolicPythonObject): VirtualPythonObject {
            return VirtualPythonObject(interpretedObj.address.address)
        }
    }
}

