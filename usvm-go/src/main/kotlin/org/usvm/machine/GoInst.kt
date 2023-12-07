package org.usvm.machine

class GoInst(
    val pointer: Long,
    val statement: String
) {
    fun isEmpty(): Boolean = pointer == 0L || statement == "nil"

    override fun toString(): String {
        return "statement: $statement, pointer: $pointer"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GoInst

        return pointer == other.pointer
    }

    override fun hashCode(): Int {
        return pointer.hashCode()
    }
}