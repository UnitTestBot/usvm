package org.usvm.domain

class GoType(
    val pointer: Long,
    val name: String
) {
    override fun toString(): String {
        return "type: $name, pointer: $pointer"
    }
}