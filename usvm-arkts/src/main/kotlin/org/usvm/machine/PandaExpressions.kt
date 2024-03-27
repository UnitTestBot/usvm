package org.usvm.machine

import io.ksmt.KAst
import io.ksmt.sort.KFp64Sort

typealias PandaFp64Sort = KFp64Sort

val KAst.pctx get() = ctx as PandaContext