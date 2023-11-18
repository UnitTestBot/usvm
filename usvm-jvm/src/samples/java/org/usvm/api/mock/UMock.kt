package org.usvm.api.mock

import org.usvm.api.Engine
import org.usvm.api.exception.UMockAssumptionViolatedException

fun assume(predicate: Boolean) {
    // TODO inline it
    if (!predicate) {
        Engine.assume(false)
        throw UMockAssumptionViolatedException()
    }
}
