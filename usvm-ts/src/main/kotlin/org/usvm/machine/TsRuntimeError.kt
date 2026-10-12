package org.usvm.machine

import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsClassType
import org.jacodb.ets.model.EtsFileSignature
import org.usvm.machine.state.TsMethodResult
import org.usvm.machine.state.TsState

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

internal fun TsState.throwTypeError() {
    val exception = memory.allocConcrete(TS_TYPE_ERROR_TYPE)
    methodResult = TsMethodResult.TsException(exception, TS_TYPE_ERROR_TYPE)
}
