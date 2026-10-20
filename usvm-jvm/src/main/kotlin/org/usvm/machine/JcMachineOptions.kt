package org.usvm.machine

enum class JcResidualCallPolicy {
    STOP,
    FRESH,
}

/** JcMachine specific options. */
data class JcMachineOptions(
    /**
     * During virtual call resolution the machine should consider all possible call implementations.
     * By default, the machine forks on few concrete implementation and ignore remaining.
     * */
    val forkOnRemainingTypes: Boolean = false,

    /**
     * Controls, whether the machine should analyze states with implicit exceptions (e.g. NPE).
     * */
    val forkOnImplicitExceptions: Boolean = true,

    /**
     * Hard constraint for maximal array size.
     * */
    val arrayMaxSize: Int = 1_500,

    /** Local experiment toggle for the interpreter's built-in method models. */
    val enableMethodApproximations: Boolean = true,

    /** Retain the pinned stdlib model's lazy length <= 50 restriction on symbolic Strings. */
    val enforceModelStringLengthBound: Boolean = true,

    /** Policy for native, bodyless, and unresolved dynamic calls after model attempts. */
    val residualCallPolicy: JcResidualCallPolicy = JcResidualCallPolicy.FRESH,
)
