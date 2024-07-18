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
val listConcatProgram = StringProgramProvider(
    """
        def list_concat(x):
            y = x + [1]
            if len(y[::-1]) == 5:
                return 1
            return 2
    """.trimIndent(),
    "list_concat",
) { listOf(PythonAnyType) }
