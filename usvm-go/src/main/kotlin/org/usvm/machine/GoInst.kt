package org.usvm.machine

class GoInst(
    val pointer: Long,
    val statement: String
) {
    override fun toString(): String {
        return "statement: $statement, pointer: $pointer"
    }

    fun isEmpty(): Boolean = pointer == 0L || statement == "nil"
}