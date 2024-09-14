package org.usvm.machine

/**
 * JcMachine specific options.
 * */
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

    /**
     * If false, invokes code of String class as-is; this is precise but might be inefficient.
     * If true, models string operations; this is efficient, but might cause loss of precision.
     */
    val useStringsApproximation: Boolean = false,
)
