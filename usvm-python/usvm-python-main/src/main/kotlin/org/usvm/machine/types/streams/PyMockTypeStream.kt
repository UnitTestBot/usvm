package org.usvm.machine.types.streams

import mu.KLogging
import org.usvm.machine.types.ConcretePythonType
import org.usvm.machine.types.ConcreteTypeNegation
import org.usvm.machine.types.InternalType
import org.usvm.machine.types.MockType
import org.usvm.machine.types.PythonAnyType
import org.usvm.machine.types.PythonType
import org.usvm.machine.types.PythonTypeSystem
import org.usvm.machine.types.VirtualPythonType
import org.usvm.types.TypesResult
import org.usvm.types.USingleTypeStream
import org.usvm.types.UTypeStream
import org.usvm.types.emptyTypeStream


class PyMockTypeStream(
    private val typeSystem: PythonTypeSystem,
    private val filter: TypeFilter,
) : UTypeStream<PythonType> {
    private fun singleOfEmpty(type: PythonType): UTypeStream<PythonType> {
        val filtered = sequenceOf(type).filter(filter).take(1).toList()
        return if (filtered.isEmpty()) {
            emptyTypeStream()
        } else {
            USingleTypeStream(typeSystem, type)
        }
    }

    override fun filterBySupertype(type: PythonType): UTypeStream<PythonType> {
        return when (type) {
            is ConcretePythonType, is MockType -> singleOfEmpty(type)
            is VirtualPythonType -> PyMockTypeStream(typeSystem, filter.addSupertype(type))
            is InternalType -> error("Should not be reachable")
        }
    }

    override fun filterBySubtype(type: PythonType): UTypeStream<PythonType> = TODO()

    override fun filterByNotSupertype(type: PythonType): UTypeStream<PythonType> {
        if (type is ConcreteTypeNegation) {
            return singleOfEmpty(type.concreteType)
        }
        return when (type) {
            is InternalType -> error("Should not be reachable")
            is VirtualPythonType, is ConcretePythonType, is MockType ->
                PyMockTypeStream(typeSystem, filter.addNotSupertype(type))
        }
    }

    override fun filterByNotSubtype(type: PythonType): UTypeStream<PythonType> = TODO()

    override fun take(n: Int): TypesResult<PythonType> {
        val result = typeSystem.findSubtypes(PythonAnyType)
            .filter(filter)
            .take(n)
            .toList()
        return if (result.isEmpty()) {
            TypesResult.EmptyTypesResult
        } else {
            if (result.first() !is MockType) { // TODO: PyMockTypeStream must start with MockType
                logger.warn("Bad start of PyMockTypeStream")
                return TypesResult.SuccessfulTypesResult(listOf(result.first()))
            }
            TypesResult.SuccessfulTypesResult(result)
        }
    }

    override val isEmpty: Boolean
        get() = take(1) is TypesResult.EmptyTypesResult

    override val commonSuperType: PythonType?
        get() = null

    companion object {
        private val logger = object : KLogging() {}.logger
    }
}
