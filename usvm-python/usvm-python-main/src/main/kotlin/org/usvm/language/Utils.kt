package org.usvm.language

import org.usvm.machine.interpreters.concrete.ConcretePythonInterpreter

fun PyCodeObject.prettyPrint(): String {
    val namespace = ConcretePythonInterpreter.getNewNamespace()
    ConcretePythonInterpreter.addObjectToNamespace(namespace, codeObject, "code")
    val name = ConcretePythonInterpreter.eval(namespace, "code.co_name")
    val nameAsStr = ConcretePythonInterpreter.getPythonObjectStr(name)
    ConcretePythonInterpreter.decref(namespace)

    return "$nameAsStr(address=${codeObject.address})"
}
