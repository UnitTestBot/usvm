package org.usvm.machine

import org.jacodb.ets.model.EtsMethod
import kotlin.time.Duration

/** Selector turn time can exceed the request because an interpreter/solver step cannot be preempted. */
data class TsEntryPointTimeQuantumEvent(
    val entrypoint: EtsMethod,
    val requested: Duration,
    val elapsed: Duration,
)
