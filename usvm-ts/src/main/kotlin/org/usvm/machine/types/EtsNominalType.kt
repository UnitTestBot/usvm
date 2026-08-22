package org.usvm.machine.types

import org.jacodb.ets.model.EtsRefType
import org.jacodb.ets.model.EtsType

internal data class EtsNominalType(val type: EtsRefType) : EtsType {
    override val typeName: String
        get() = "NominalType $type"

    override fun <R> accept(visitor: EtsType.Visitor<R>): R {
        error("Should not be called")
    }
}
