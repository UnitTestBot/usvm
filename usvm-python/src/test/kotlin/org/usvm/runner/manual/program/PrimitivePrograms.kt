package org.usvm.runner.manual.program

import org.usvm.machine.types.PythonAnyType

/**
 * Use this declaration for simple manual checks.
 * */
val sampleStringFunction = StringProgramProvider(
    """
        def f(x):
            assert x != [1, 2, 3]
    """.trimIndent(),
    "f"
) { typeSystem -> listOf(typeSystem.pythonList) }

/**
 * Sample of a function that cannot be covered right now.
 * */
val tupleConcatProgram = StringProgramProvider(
    """
        def tuple_concat(x, y):
            z = x + y
            return z + (1, 2, 3)
    """.trimIndent(),
    "tuple_concat",
) { listOf(PythonAnyType, PythonAnyType) }
