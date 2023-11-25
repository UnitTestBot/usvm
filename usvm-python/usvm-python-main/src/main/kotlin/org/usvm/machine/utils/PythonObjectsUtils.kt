package org.usvm.machine.utils

import org.usvm.machine.interpreters.ConcretePythonInterpreter
import org.usvm.machine.interpreters.PythonObject

fun getCodeOfFunction(function: PythonObject): PythonObject {
    val namespace = ConcretePythonInterpreter.getNewNamespace()
    ConcretePythonInterpreter.addObjectToNamespace(namespace, function, "f")
    return ConcretePythonInterpreter.eval(namespace, "f.__code__").also {
        ConcretePythonInterpreter.decref(namespace)
    }
}