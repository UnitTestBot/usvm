package org.usvm.language.types.streams

import org.usvm.language.types.*
import org.usvm.types.TypesResult
import org.usvm.types.USingleTypeStream
import org.usvm.types.UTypeStream
import org.usvm.types.emptyTypeStream

class PyGenericTypeStream(
    private val typeSystem: PythonTypeSystem,
    val genericType: GenericType,
    private val filter: TypeFilter,
    private val innerTypeStream: UTypeStream<PythonType>
): UTypeStream<PythonType> {
    val depth: Int
        get() = (innerTypeStream as? PyGenericTypeStream)?.depth ?: 1
    override fun filterBySupertype(type: PythonType): UTypeStream<PythonType> =
        when (type) {
            is InternalType -> error("Should not be reachable")
            is VirtualPythonType ->
                PyGenericTypeStream(typeSystem, genericType, filter.addSupertype(type), innerTypeStream)
            is MockType -> emptyTypeStream()
            is PrimitiveConcretePythonType -> emptyTypeStream()
            is ArrayLikeConcretePythonType -> {
                if (type.id != genericType.typeWithoutInner.id || type.innerType == null) {
                    emptyTypeStream()
                } else {
                    when (type.innerType) {
                        is ConcretePythonType -> singleOrEmpty(type)
                        is GenericType ->
                            PyGenericTypeStream(typeSystem, genericType, filter, innerTypeStream.filterBySupertype(type.innerType))
                        else -> error("Should not be reachable")
                    }
                }
            }
        }

    private fun singleOrEmpty(type: PythonType): UTypeStream<PythonType> {
        if (type !is ArrayLikeConcretePythonType || type.innerType == null || type.id != genericType.typeWithoutInner.id) {
            return emptyTypeStream()
        }
        val filtered = sequenceOf(type).filter(filter).take(1).toList()
        return if (filtered.isEmpty()) {
            emptyTypeStream()
        } else {
            USingleTypeStream(typeSystem, type)
        }
    }

    override fun filterBySubtype(type: PythonType): UTypeStream<PythonType> {
        TODO("Not yet implemented")
    }

    override fun filterByNotSupertype(type: PythonType): UTypeStream<PythonType> {
        if (type is ConcreteTypeNegation) {
            return if (type.concreteType is ArrayLikeConcretePythonType && type.concreteType.id == genericType.typeWithoutInner.id) {
                singleOrEmpty(type.concreteType)
            } else {
                emptyTypeStream()
            }
        }
        return when (type) {
            is InternalType -> error("Should not be reachable")
            is MockType -> this
            is PrimitiveConcretePythonType -> this
            is VirtualPythonType, is ArrayLikeConcretePythonType ->
                PyGenericTypeStream(typeSystem, genericType, filter.addNotSupertype(type), innerTypeStream)
        }
    }

    override fun filterByNotSubtype(type: PythonType): UTypeStream<PythonType> {
        TODO("Not yet implemented")
    }

    override fun take(n: Int): TypesResult<PythonType> {
        if (n <= 0)
            return TypesResult.EmptyTypesResult
        val (innerSeq, wasTimeout) = when (val inner = innerTypeStream.take(n)) {
            is TypesResult.EmptyTypesResult -> return TypesResult.EmptyTypesResult
            is TypesResult.SuccessfulTypesResult -> inner.types to false
            is TypesResult.TypesResultWithExpiredTimeout -> inner.collectedTypes to true
        }
        val outer = innerSeq.map { genericType.typeWithoutInner.substitute(it) }.asSequence()
        val result = outer.filter(filter).take(n).toList()
        return if (result.isEmpty())
            TypesResult.EmptyTypesResult
        else if (!wasTimeout) {
            TypesResult.SuccessfulTypesResult(result)
        } else {
            TypesResult.TypesResultWithExpiredTimeout(result)
        }
    }
    override val isEmpty: Boolean
        get() = take(1) is TypesResult.EmptyTypesResult
    override val commonSuperType: PythonType?
        get() = null
}