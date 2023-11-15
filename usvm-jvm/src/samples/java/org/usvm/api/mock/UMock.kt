package org.usvm.api.mock

import org.usvm.api.Engine

fun assume(predicate: Boolean) {
    // TODO inline it
    Engine.assume(predicate)
}
