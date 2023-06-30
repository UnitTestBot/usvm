package org.usvm.api.mock

import org.usvm.api.exception.UMockAssumptionViolatedException

fun assume(predicate: Boolean) {
    if (!predicate) {
        throw UMockAssumptionViolatedException()
    }
}