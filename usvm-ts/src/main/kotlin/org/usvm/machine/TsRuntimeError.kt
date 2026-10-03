package org.usvm.machine

import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsFileSignature

private val runtimeFile = EtsFileSignature(
    projectName = "usvm.ts.runtime",
    fileName = "TypeError",
)
private val typeErrorSignature = EtsClassSignature(
    name = "TypeError",
    file = runtimeFile,
)

/** Type identity of terminal JavaScript TypeError exceptions produced by the interpreter. */
internal val TS_TYPE_ERROR_TYPE = EtsClassType(typeErrorSignature)
