package org.usvm.machine.interpreters.concrete.utils

private const val MAX_NEEDED_MASK_BYTE_NUMBER: Int = 12
private const val ALL_SLOTS_BYTE: Int = 0b11111111

class VirtualPythonObject(
    @JvmField
    val interpretedObjRef: Int,
    val slotMask: ByteArray = List(MAX_NEEDED_MASK_BYTE_NUMBER) { ALL_SLOTS_BYTE.toByte() }.toByteArray(),
)
