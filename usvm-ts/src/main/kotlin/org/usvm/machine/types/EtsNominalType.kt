package org.usvm.machine.types

import org.jacodb.ets.model.EtsRefType
import org.jacodb.ets.model.EtsType

/**
 * Runtime membership in the declared class hierarchy, rather than TypeScript structural assignability.
 * For example, unrelated classes A and B can have identical fields but `new A() instanceof B` is false.
 * This wrapper prevents field-shape constraints from being used as JavaScript prototype-chain checks.
 */
internal data class EtsNominalType(val type: EtsRefType) : EtsType {
    override val typeName: String
        get() = "NominalType $type"

    override fun <R> accept(visitor: EtsType.Visitor<R>): R {
        error("Should not be called")
    }
}
