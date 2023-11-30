package org.usvm.machine

class GoMethod(
    val pointer: Long,
    val name: String
) {
    override fun toString(): String {
        return "method: $name, pointer: $pointer"
    }
}