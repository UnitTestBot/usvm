package org.usvm.machine.rendering

import org.usvm.language.types.*
import org.usvm.machine.interpreters.ConcretePythonInterpreter
import org.usvm.machine.interpreters.PythonObject
import org.usvm.machine.interpreters.ConcretePythonInterpreter.emptyNamespace

class DefaultValueProvider(private val typeSystem: PythonTypeSystem) {
    fun provide(type: PythonType): PythonObject {
        require(type is ConcretePythonType)

        return when (type) {
            typeSystem.pythonInt -> ConcretePythonInterpreter.eval(emptyNamespace, "0")
            typeSystem.pythonBool -> ConcretePythonInterpreter.eval(emptyNamespace, "False")
            typeSystem.pythonFloat -> ConcretePythonInterpreter.eval(emptyNamespace, "0.0")
            typeSystem.pythonObjectType -> ConcretePythonInterpreter.eval(emptyNamespace, "object()")
            typeSystem.pythonNoneType -> ConcretePythonInterpreter.eval(emptyNamespace, "None")
            typeSystem.pythonList -> ConcretePythonInterpreter.eval(emptyNamespace, "[]")
            typeSystem.pythonTuple -> ConcretePythonInterpreter.eval(emptyNamespace, "tuple()")
            typeSystem.pythonStr -> ConcretePythonInterpreter.eval(emptyNamespace, "''")
            typeSystem.pythonSlice -> ConcretePythonInterpreter.eval(emptyNamespace, "slice(0, 1, 1)")
            typeSystem.pythonDict -> ConcretePythonInterpreter.eval(emptyNamespace, "dict()")
            typeSystem.pythonSet -> ConcretePythonInterpreter.eval(emptyNamespace, "set()")
            else -> {
                val ref = typeSystem.addressOfConcreteType(type)
                if (ConcretePythonInterpreter.typeHasStandardNew(ref)) {
                    ConcretePythonInterpreter.callStandardNew(ref)
                } else {
                    error("DefaultValueProvider for type $type is not implemented")
                }
            }
        }
    }
}