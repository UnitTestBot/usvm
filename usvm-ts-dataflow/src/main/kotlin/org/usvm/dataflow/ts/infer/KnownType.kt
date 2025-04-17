package org.usvm.dataflow.ts.infer

import org.jacodb.ets.model.EtsEntity
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsParameterRef
import org.jacodb.ets.model.EtsThis
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUnknownType
import org.usvm.dataflow.ts.util.type

fun EtsEntity.tryGetKnownType(method: EtsMethod): EtsType {
    if (this is EtsLocal) {
        return type
    }

    if (this is EtsParameterRef) {
        return method.parameters[index].type
    }

    if (this is EtsThis) {
        method.enclosingClass?.type?.let { return it }
    }

    return EtsUnknownType
}
