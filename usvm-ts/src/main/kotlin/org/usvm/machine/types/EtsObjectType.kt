package org.usvm.machine.types

import org.jacodb.ets.model.EtsRefType
import org.jacodb.ets.model.EtsType

/** Internal runtime-object supertype: class instances, arrays and functions, excluding primitive payloads. */
internal data object EtsObjectType : EtsRefType {
    override val typeName: String get() = "RuntimeObject"

    override fun <R> accept(visitor: EtsType.Visitor<R>): R = error("Internal runtime type")
}
