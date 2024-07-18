package org.usvm.machine.utils

import org.usvm.machine.interpreters.concrete.ConcretePythonInterpreter
import org.usvm.machine.interpreters.concrete.PyObject

fun isGenerator(funcRef: PyObject): Boolean {
    val namespace = ConcretePythonInterpreter.getNewNamespace()
    ConcretePythonInterpreter.addObjectToNamespace(namespace, funcRef, "x")
    ConcretePythonInterpreter.concreteRun(namespace, "import inspect")
    val res = ConcretePythonInterpreter.eval(namespace, "inspect.isgeneratorfunction(x)", printErrorMsg = true)
    ConcretePythonInterpreter.decref(namespace)
    return ConcretePythonInterpreter.getPythonObjectRepr(res) == "True"
}

fun unfoldGenerator(funcRef: PyObject): PyObject {
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
