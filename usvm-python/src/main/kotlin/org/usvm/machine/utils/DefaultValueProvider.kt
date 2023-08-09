package org.usvm.machine.utils

import org.usvm.language.types.*
import org.usvm.machine.interpreters.ConcretePythonInterpreter
import org.usvm.machine.interpreters.PythonObject
import org.usvm.machine.interpreters.emptyNamespace

class DefaultValueProvider(private val typeSystem: PythonTypeSystem) {
    fun provide(type: PythonType): PythonObject {
        require(typeSystem.isInstantiable(type))

        return when (type) {
            pythonInt -> ConcretePythonInterpreter.eval(emptyNamespace, "0")
            pythonBool -> ConcretePythonInterpreter.eval(emptyNamespace, "False")
            pythonList -> ConcretePythonInterpreter.eval(emptyNamespace, "[]")
            pythonObjectType -> ConcretePythonInterpreter.eval(emptyNamespace, "object()")
            pythonNoneType -> ConcretePythonInterpreter.eval(emptyNamespace, "None")
            else -> TODO()
        }
    }
}