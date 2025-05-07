package org.usvm.machine.types

import org.jacodb.ets.model.EtsType

/**
 * An auxiliary type is a type that is not directly represented in the TS class hierarchy.
 * Can be used as a JS-like type containing set of properties.
 */
class AuxiliaryType(val properties: Set<String>) : EtsType {
    override fun <R> accept(visitor: EtsType.Visitor<R>): R {
        error("Should not be called")
    }

    override val typeName: String
        get() = "AuxiliaryType ${properties.toSortedSet()}"
}
