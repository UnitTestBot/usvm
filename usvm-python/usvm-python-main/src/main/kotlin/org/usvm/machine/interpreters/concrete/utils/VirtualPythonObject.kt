package org.usvm.machine.interpreters.concrete.utils

class VirtualPythonObject(
    @JvmField
    val interpretedObjRef: Int,
    val slotMask: ByteArray = List(12) { 0b11111111.toByte() }.toByteArray()
)
