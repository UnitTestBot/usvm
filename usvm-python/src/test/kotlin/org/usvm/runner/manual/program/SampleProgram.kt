package org.usvm.runner.manual.program

import org.usvm.machine.types.PythonAnyType

/**
 * Use this for manual tests of samples.
 * */
val sampleFunction = SampleProgramProvider(
    listOf(("SimpleTypeInference" to "use_str_eq") to listOf(PythonAnyType))
)
