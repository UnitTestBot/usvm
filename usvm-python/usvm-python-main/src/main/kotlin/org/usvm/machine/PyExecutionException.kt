package org.usvm.machine

import org.usvm.machine.interpreters.concrete.PyObject

sealed class PyExecutionException : RuntimeException()

class CPythonExecutionException(
    val pythonExceptionValue: PyObject? = null,
    val pythonExceptionType: PyObject? = null,
) : PyExecutionException()

sealed class PyExecutionExceptionFromJava : PyExecutionException()

class UnregisteredVirtualOperation : PyExecutionExceptionFromJava()

class BadModelException : PyExecutionExceptionFromJava()

class InstructionLimitExceededException : PyExecutionExceptionFromJava()

class CancelledExecutionException : PyExecutionExceptionFromJava()
