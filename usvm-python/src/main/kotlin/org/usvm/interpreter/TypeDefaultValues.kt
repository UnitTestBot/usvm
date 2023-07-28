package org.usvm.interpreter

import org.usvm.language.types.*

object TypeDefaultValueProvider {
    fun provide(type: PythonType): PythonObject {
        require(PythonTypeSystem.isInstantiable(type))

        return when (type) {
            pythonInt -> ConcretePythonInterpreter.eval(emptyNamespace, "0")
            pythonBool -> ConcretePythonInterpreter.eval(emptyNamespace, "False")
            pythonList -> ConcretePythonInterpreter.eval(emptyNamespace, "[]")
            else -> TODO()
        }
    }
}