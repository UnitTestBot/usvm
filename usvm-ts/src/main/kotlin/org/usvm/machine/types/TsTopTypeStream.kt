package org.usvm.machine.types

import org.jacodb.ets.model.EtsAnyType
import org.jacodb.ets.model.EtsPrimitiveType
import org.jacodb.ets.model.EtsType
import org.jacodb.ets.model.EtsUnknownType
import org.usvm.types.TypesResult
import org.usvm.types.TypesResult.Companion.toTypesResult
import org.usvm.types.USupportTypeStream
import org.usvm.types.UTypeStream
import org.usvm.types.emptyTypeStream

class TsTopTypeStream(
    private val typeSystem: TsTypeSystem,
    private val primitiveTypes: List<EtsType> = TsTypeSystem.primitiveTypes.toList(),
    // We treat unknown as a top type in this system
    private val anyTypeStream: UTypeStream<EtsType> = USupportTypeStream.from(typeSystem, EtsUnknownType),
) : UTypeStream<EtsType> {

    override fun filterBySupertype(type: EtsType): UTypeStream<EtsType> {
        if (type is EtsPrimitiveType) return emptyTypeStream()

        return anyTypeStream.filterBySupertype(type)
    }

    override fun filterBySubtype(type: EtsType): UTypeStream<EtsType> {
        return anyTypeStream.filterBySubtype(type)
    }

    override fun filterByNotSupertype(type: EtsType): UTypeStream<EtsType> {
        if (type in primitiveTypes) {
            val updatedPrimitiveTypes = primitiveTypes.remove(type)

            if (updatedPrimitiveTypes.isEmpty()) return anyTypeStream

            return TsTopTypeStream(typeSystem, updatedPrimitiveTypes, anyTypeStream)
        }

        return TsTopTypeStream(typeSystem, primitiveTypes, anyTypeStream.filterByNotSupertype(type))
    }

    override fun filterByNotSubtype(type: EtsType): UTypeStream<EtsType> {
        if (type in primitiveTypes) {
            val updatedPrimitiveTypes = primitiveTypes.remove(type)

            if (updatedPrimitiveTypes.isEmpty()) return anyTypeStream

            return TsTopTypeStream(typeSystem, updatedPrimitiveTypes, anyTypeStream)
        }

        return TsTopTypeStream(typeSystem, primitiveTypes, anyTypeStream.filterByNotSubtype(type))
    }

    override fun take(n: Int): TypesResult<EtsType> {
        if (n <= primitiveTypes.size) {
            return primitiveTypes.toTypesResult(wasTimeoutExpired = false)
        }

        val types = primitiveTypes.toMutableList()
        return when (val remainingTypes = anyTypeStream.take(n - primitiveTypes.size)) {
            TypesResult.EmptyTypesResult -> types.toTypesResult(wasTimeoutExpired = false)
            is TypesResult.SuccessfulTypesResult -> {
                val allTypes = types + remainingTypes.types
                allTypes.toTypesResult(wasTimeoutExpired = false)
            }

            is TypesResult.TypesResultWithExpiredTimeout -> {
                val allTypes = types + remainingTypes.collectedTypes
                allTypes.toTypesResult(wasTimeoutExpired = true)
            }
        }
    }

    override val isEmpty: Boolean?
        get() = anyTypeStream.isEmpty?.let { primitiveTypes.isEmpty() }

    override val commonSuperType: EtsType?
        get() = EtsUnknownType.takeIf { !(isEmpty ?: true) }

    private fun <T> List<T>.remove(x: T): List<T> = this.filterNot { it == x }
}
