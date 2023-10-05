package org.usvm.machine.utils

import org.usvm.language.types.*
import org.usvm.machine.interpreters.ConcretePythonInterpreter
import org.usvm.machine.interpreters.PythonObject
import org.usvm.machine.interpreters.ConcretePythonInterpreter.emptyNamespace

class DefaultValueProvider(private val typeSystem: PythonTypeSystem) {
    fun provide(type: PythonType): PythonObject {
        require(typeSystem.isInstantiable(type))

        return when (type) {
            typeSystem.pythonInt -> ConcretePythonInterpreter.eval(emptyNamespace, "0")
            typeSystem.pythonBool -> ConcretePythonInterpreter.eval(emptyNamespace, "False")
            typeSystem.pythonList -> ConcretePythonInterpreter.eval(emptyNamespace, "[]")
            typeSystem.pythonObjectType -> ConcretePythonInterpreter.eval(emptyNamespace, "object()")
            typeSystem.pythonNoneType -> ConcretePythonInterpreter.eval(emptyNamespace, "None")
            typeSystem.pythonFloat -> ConcretePythonInterpreter.eval(emptyNamespace, "0.0")
            else -> TODO()
        }
    }
}