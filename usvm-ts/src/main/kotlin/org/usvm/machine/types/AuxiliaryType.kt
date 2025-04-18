package org.usvm.machine.types

import org.jacodb.ets.model.EtsFieldSignature
import org.jacodb.ets.model.EtsType

// TODO string name?
class AuxiliaryType(val properties: Set<String>) : EtsType {
    override fun <R> accept(visitor: EtsType.Visitor<R>): R {
        error("Should not be called")
    }

    override val typeName: String
        get() = "AuxiliaryType"
}