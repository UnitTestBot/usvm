package org.usvm.machine

import org.jacodb.ets.base.EtsAnyType
import org.jacodb.ets.base.EtsBooleanType
import org.jacodb.ets.base.EtsNumberType
import org.jacodb.ets.base.EtsPrimitiveType
import org.jacodb.ets.base.EtsType
import org.jacodb.ets.base.EtsUnknownType
import org.jacodb.ets.model.EtsScene
import org.usvm.types.TypesResult
import org.usvm.types.TypesResult.Companion.toTypesResult
import org.usvm.types.USupportTypeStream
import org.usvm.types.UTypeStream
import org.usvm.types.UTypeSystem
import org.usvm.types.emptyTypeStream
import kotlin.time.Duration

class TSTypeSystem(
    override val typeOperationsTimeout: Duration,
    val project: EtsScene,
) : UTypeSystem<EtsType> {

    companion object {
        // TODO: add more primitive types (string, etc.) once supported
        val primitiveTypes = sequenceOf(EtsNumberType, EtsBooleanType)
    }

    override fun isSupertype(supertype: EtsType, type: EtsType): Boolean = when {
        supertype == type -> true
        supertype == EtsUnknownType || supertype == EtsAnyType -> true
        else -> false
    }

    override fun hasCommonSubtype(type: EtsType, types: Collection<EtsType>): Boolean = when {
        type is EtsPrimitiveType -> types.isEmpty()
        else -> false
    }

    override fun isFinal(type: EtsType): Boolean = when (type) {
        is EtsPrimitiveType -> true
        is EtsUnknownType -> false
        is EtsAnyType -> false
        else -> false
    }

    override fun isInstantiable(type: EtsType): Boolean = when (type) {
        is EtsPrimitiveType -> true
        is EtsUnknownType -> true
        is EtsAnyType -> true
        else -> false
    }

    override fun findSubtypes(type: EtsType): Sequence<EtsType> = when (type) {
        is EtsPrimitiveType -> emptySequence()
        is EtsUnknownType -> primitiveTypes
        is EtsAnyType -> primitiveTypes
        else -> emptySequence()
    }

    private val topTypeStream by lazy { TSTopTypeStream(this) }

    override fun topTypeStream(): UTypeStream<EtsType> = topTypeStream
}

class TSTopTypeStream(
    private val typeSystem: TSTypeSystem,
    private val primitiveTypes: List<EtsType> = TSTypeSystem.primitiveTypes.toList(),
    // Currently only EtsUnknownType was encountered and viewed as any type.
    // However, there is EtsAnyType that represents any type.
    // TODO: replace EtsUnknownType with further TSTypeSystem implementation.
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

            return TSTopTypeStream(typeSystem, updatedPrimitiveTypes, anyTypeStream)
        }

        return TSTopTypeStream(typeSystem, primitiveTypes, anyTypeStream.filterByNotSupertype(type))
    }

    override fun filterByNotSubtype(type: EtsType): UTypeStream<EtsType> {
        if (type in primitiveTypes) {
            val updatedPrimitiveTypes = primitiveTypes.remove(type)

            if (updatedPrimitiveTypes.isEmpty()) return anyTypeStream

            return TSTopTypeStream(typeSystem, updatedPrimitiveTypes, anyTypeStream)
        }

        return TSTopTypeStream(typeSystem, primitiveTypes, anyTypeStream.filterByNotSubtype(type))
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
