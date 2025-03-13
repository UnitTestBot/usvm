package org.usvm.model

interface TypeName {
    val typeName: String
}

data class TypeNameImpl(
    override val typeName: String,
) : TypeName {
    override fun toString(): String {
        return typeName
    }
}
