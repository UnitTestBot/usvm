package org.usvm.machine.symbolicobjects.rendering

import org.usvm.language.types.ConcretePythonType
import org.usvm.language.types.GenericType
import org.usvm.language.types.PythonType
import org.usvm.language.types.PythonTypeSystem
import org.usvm.machine.interpreters.concrete.ConcretePythonInterpreter
import org.usvm.python.model.PyCompositeObject
import org.usvm.python.model.PyIdentifier
import org.usvm.python.model.PyObjectModel
import org.usvm.python.model.PyPrimitive

class DefaultPyObjectModelProvider(private val typeSystem: PythonTypeSystem) {
    fun provide(type: PythonType): PyObjectModel {
        if (type is GenericType) {
            return provide(type.typeWithoutInner)
        }

        require(type is ConcretePythonType)

        return when (type) {
            typeSystem.pythonInt -> PyPrimitive("0")
            typeSystem.pythonBool -> PyPrimitive("False")
            typeSystem.pythonFloat -> PyPrimitive("0.0")
            typeSystem.pythonObjectType -> PyCompositeObject(type.id, emptyList())
            typeSystem.pythonNoneType -> PyPrimitive("None")
            typeSystem.pythonList -> PyCompositeObject(type.id, emptyList())
            typeSystem.pythonTuple -> PyCompositeObject(type.id, emptyList())
            typeSystem.pythonStr -> PyCompositeObject(type.id, emptyList())
            typeSystem.pythonSlice -> PyCompositeObject(type.id, listOf(PyPrimitive("0"), PyPrimitive("1")))
            typeSystem.pythonDict -> PyCompositeObject(type.id, emptyList())
            typeSystem.pythonSet -> PyCompositeObject(type.id, emptyList())
            else -> {
                val ref = typeSystem.addressOfConcreteType(type)
                if (ConcretePythonInterpreter.typeHasStandardNew(ref)) {
                    PyCompositeObject(PyIdentifier("builtins", "object.__new__"), listOf(type.id))
                } else {
                    error("DefaultValueProvider for type $type is not implemented")
                }
            }
        }
    }
}