package org.usvm.machine.utils

import org.usvm.machine.interpreters.ConcretePythonInterpreter
import org.usvm.machine.interpreters.PythonObject

/*
fun isGenerator(funcRef: PythonObject): Boolean {
    TODO()
}
*/

fun unfoldGenerator(funcRef: PythonObject): PythonObject {
    val namespace = ConcretePythonInterpreter.getNewNamespace()
    ConcretePythonInterpreter.addObjectToNamespace(namespace, funcRef, "f")
    ConcretePythonInterpreter.concreteRun(
        namespace,
        """
            def new_f(*args):
                result = []
                for elem in f(*args):
                    result.append(elem)
                return result
        """.trimIndent()
    )
    return ConcretePythonInterpreter.eval(namespace, "new_f").also {
        ConcretePythonInterpreter.decref(namespace)
    }
}