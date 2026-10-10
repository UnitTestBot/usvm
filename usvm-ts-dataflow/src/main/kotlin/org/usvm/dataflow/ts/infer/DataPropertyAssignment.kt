package org.usvm.dataflow.ts.infer

import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsDefineDataPropertyStmt
import org.jacodb.ets.model.EtsFieldSignature
import org.jacodb.ets.model.EtsInstanceFieldRef
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsStringConstant

/** Object-literal data properties use the same access paths as ordinary field writes. */
internal fun EtsStmt.dataflowAssignment(): EtsAssignStmt? = when (this) {
    is EtsAssignStmt -> {
        this
    }
    is EtsDefineDataPropertyStmt -> {
        val name = (key as? EtsStringConstant)?.value
        val owner = (target.type as? EtsClassType)?.signature
        if (name == null || owner == null) {
            null
        } else {
            val field = EtsFieldSignature(enclosingClass = owner, name = name, type = value.type)
            val destination = EtsInstanceFieldRef(instance = target, field = field, type = value.type)
            EtsAssignStmt(location = location, lhv = destination, rhv = value)
        }
    }
    else -> {
        null
    }
}
