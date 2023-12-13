package org.usvm.domain

class GoMethod(
    val pointer: Long,
    val name: String
) {
    override fun toString(): String {
        return "method: $name, pointer: $pointer"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GoMethod

        return pointer == other.pointer
    }

    override fun hashCode(): Int {
        return pointer.hashCode()
    }
}