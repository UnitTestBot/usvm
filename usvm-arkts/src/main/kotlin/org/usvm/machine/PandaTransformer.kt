package org.usvm.machine

import io.ksmt.sort.KFp64Sort
import org.jacodb.panda.dynamic.api.PandaType
import org.usvm.UTransformer

// TODO size sort
interface PandaTransformer: UTransformer<PandaType, KFp64Sort> {
    fun transform(expr: PandaConcreteString)
}