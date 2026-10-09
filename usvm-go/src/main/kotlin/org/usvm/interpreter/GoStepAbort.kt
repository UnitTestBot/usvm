package org.usvm.interpreter

/** Stops the current hop when StepScope has forked to a panic or discarded an infeasible state. */
internal class GoStepAbort : RuntimeException("Current Go step cannot continue", null, false, false)
