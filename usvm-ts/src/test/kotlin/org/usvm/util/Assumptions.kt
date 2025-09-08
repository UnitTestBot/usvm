@file:OptIn(ExperimentalContracts::class)

package org.usvm.util

import org.junit.jupiter.api.Assumptions
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

fun abort(message: String = ""): Nothing {
    Assumptions.abort<Unit>(message)
    error("Unreachable")
}

fun assumeTrue(assumption: Boolean, message: String = "Assumption failed") {
    contract { returns() implies assumption }
    Assumptions.assumeTrue(assumption, message)
}

fun assumeFalse(assumption: Boolean, message: String = "Assumption failed") {
    contract { returns() implies !assumption }
    Assumptions.assumeFalse(assumption, message)
}

fun <T : Any> assumeNotNull(value: T?, message: String = "Value should not be null"): T {
    contract { returns() implies (value != null) }
    assumeTrue(value != null, message)
    return value
}
