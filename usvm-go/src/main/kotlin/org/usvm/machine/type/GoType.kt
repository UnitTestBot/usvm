package org.usvm.machine.type

import org.usvm.api.UnknownTypeException

enum class Type(val value: Byte) {
    UNKNOWN(0),
    BOOL(1),
    INT8(2),
    INT16(3),
    INT32(4),
    INT64(5),
    UINT8(6),
    UINT16(7),
    UINT32(8),
    UINT64(9),
    FLOAT32(10),
    FLOAT64(11);

    fun isSigned(): Boolean = when (this) {
        BOOL, INT8, INT16, INT32, INT64, FLOAT32, FLOAT64 -> true
        UINT8, UINT16, UINT32, UINT64 -> false
        else -> false
    }

    companion object {
        private val values = values()

        fun valueOf(value: Byte) = values.firstOrNull { it.value == value } ?: throw UnknownTypeException()
    }
}