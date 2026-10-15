package org.usvm.util

import org.junit.jupiter.api.Assumptions

fun abort(message: String = ""): Nothing {
    Assumptions.abort<Unit>(message)
    error("Unreachable")
}
