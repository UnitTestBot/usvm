package org.usvm.language.types.streams

import org.usvm.language.types.*
import org.usvm.types.*

class PyMockTypeStream(
    private val typeSystem: PythonTypeSystem,
    private val filter: TypeFilter
) : UTypeStream<PythonType> {
    override fun filterBySupertype(type: PythonType): UTypeStream<PythonType> {
        if (type is GenericType) {
            if (getTopLevelTypeStreamConfig(type.typeWithoutInner) != ArrayLikeTypeConfig.Homogeneous)
                return emptyTypeStream()
            return PyGenericTypeStream(typeSystem, type, filter, typeSystem.topTypeStream())
        }
        return when (type) {
            is ConcretePythonType, is MockType -> USingleTypeStream(typeSystem, type)
            is VirtualPythonType -> PyMockTypeStream(typeSystem, filter.addSupertype(type))
            is InternalType -> error("Should not be reachable")
        }
    }

    override fun filterBySubtype(type: PythonType): UTypeStream<PythonType> =
        when (type) {
            is VirtualPythonType -> emptyTypeStream()
            is InternalType -> error("Should not be reachable")
            is ConcretePythonType, is MockType -> USingleTypeStream(typeSystem, type)
        }

    override fun filterByNotSupertype(type: PythonType): UTypeStream<PythonType> {
        if (type is ConcreteTypeNegation) {
            return USingleTypeStream(typeSystem, type.concreteType)
        }
        return when (type) {
            is InternalType -> error("Should not be reachable")
            is VirtualPythonType, is ConcretePythonType, is MockType ->
                PyMockTypeStream(typeSystem, filter.addNotSupertype(type))
        }
    }

    override fun filterByNotSubtype(type: PythonType): UTypeStream<PythonType> =
        when (type) {
            is InternalType -> error("Should not be reachable")
            is VirtualPythonType, is ConcretePythonType, is MockType ->
                PyMockTypeStream(typeSystem, filter.addNotSubtype(type))
        }

    override fun take(n: Int): TypesResult<PythonType> {
        val result = typeSystem.findSubtypes(PythonAnyType)
            .filter(filter)
            .take(n)
            .toList()
            .map { if (it is ArrayLikeConcretePythonType) getTopLevelTypeStreamVariant(it) else it }
        return if (result.isEmpty()) {
            TypesResult.EmptyTypesResult
        } else {
            require(result.first() is MockType) {
                "PyMockTypeStream must start with MockType"
            }
            TypesResult.SuccessfulTypesResult(result)
        }
    }

    override val isEmpty: Boolean
        get() = take(1) is TypesResult.EmptyTypesResult

    override val commonSuperType: PythonType?
        get() = null
}