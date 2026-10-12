package org.usvm.machine.expr

import org.usvm.machine.throwTypeError

/** Primitive strings are immutable; assignment values and effects were already evaluated by the interpreter. */
internal fun TsExprResolver.handleStringPropertyWrite(): Unit? {
    val strictMode = scope.calcOnState { lastEnteredMethod.isStrictMode }
        ?: throw UnsupportedOperationException("String property writes require lexical strictness metadata")
    if (!strictMode) {
        return Unit
    }

    scope.doWithState {
        throwTypeError()
    }

    return null
}
