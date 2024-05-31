package org.usvm.runner.manual.program

import org.usvm.machine.types.PythonAnyType

/**
 * Use this declaration for simple manual checks.
 * */
val sampleStringFunction = StringProgramProvider(
    """
        def f(x):
            assert x != "hello"
    """.trimIndent(),
    listOf("f" to listOf(PythonAnyType))
)

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
    listOf("list_concat" to listOf(PythonAnyType))
)
