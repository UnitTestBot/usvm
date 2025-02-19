package org.usvm.machine.types

import com.jetbrains.rd.framework.util.RdCoroutineScope.Companion.override
import org.jacodb.ets.base.EtsRefType
import org.jacodb.ets.base.EtsType
import org.jacodb.ets.model.EtsFieldSignature

/**
 * An artificial type that represents a set of properties.
 */
class AuxiliaryType(val properties: Set<EtsFieldSignature>) : EtsRefType {
    override val typeName: String = "AuxiliaryType"

    override fun <R> accept(visitor: EtsType.Visitor<R>): R {
        error("Should not be called")
    }
}