package org.usvm.runner.manual.program

import org.usvm.machine.types.PythonAnyType

/**
 * Use this for manual tests of samples.
 * */
val sampleFunction = SampleProgramProvider(
    "SimpleTypeInference",
    "use_str_eq",
) { listOf(PythonAnyType) }
