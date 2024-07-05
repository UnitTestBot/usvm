package org.usvm.machine

fun ConcolicRunContext.extractCurState(): PyState {
    val result = curState
    requireNotNull(result) {
        "`extractCurState` should be called when you are sure that " +
            "curState is non-null. It is null now."
    }
    return result
}
