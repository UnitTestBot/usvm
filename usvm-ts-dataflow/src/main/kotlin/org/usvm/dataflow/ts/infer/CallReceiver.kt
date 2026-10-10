package org.usvm.dataflow.ts.infer

import org.jacodb.ets.model.EtsCallExpr
import org.jacodb.ets.model.EtsInstanceCallExpr
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsPtrCallExpr

internal fun EtsCallExpr.instanceReceiver(): EtsLocal? = when (this) {
    is EtsInstanceCallExpr -> instance
    is EtsPtrCallExpr -> receiver
    else -> null
}
