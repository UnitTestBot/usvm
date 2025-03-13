package org.usvm.machine

import org.usvm.model.TsAnyType
import org.usvm.model.TsBooleanType
import org.usvm.model.TsNumberType
import org.usvm.model.TsPrimitiveType
import org.usvm.model.TsScene
import org.usvm.model.TsType
import org.usvm.model.TsUnknownType
import org.usvm.types.TypesResult
import org.usvm.types.TypesResult.Companion.toTypesResult
import org.usvm.types.USupportTypeStream
import org.usvm.types.UTypeStream
import org.usvm.types.UTypeSystem
import org.usvm.types.emptyTypeStream
import kotlin.time.Duration

// TODO this is draft, should be replaced with real implementation
class TsTypeSystem(
    val scene: TsScene,
    override val typeOperationsTimeout: Duration,
) : UTypeSystem<TsType> {

    companion object {
        // TODO: add more primitive types (string, etc.) once supported
        val primitiveTypes = sequenceOf(TsNumberType, TsBooleanType)
    }

    override fun isSupertype(supertype: TsType, type: TsType): Boolean = when {
        supertype == type -> true
        supertype == TsUnknownType || supertype == TsAnyType -> true
        else -> false
    }

    override fun hasCommonSubtype(type: TsType, types: Collection<TsType>): Boolean = when {
        type is TsPrimitiveType -> types.isEmpty()
        else -> false
    }

    override fun isFinal(type: TsType): Boolean = when (type) {
        is TsPrimitiveType -> true
        is TsUnknownType -> false
        is TsAnyType -> false
        else -> false
    }

    override fun isInstantiable(type: TsType): Boolean = when (type) {
        is TsPrimitiveType -> true
        is TsUnknownType -> true
        is TsAnyType -> true
        else -> false
    }

    override fun findSubtypes(type: TsType): Sequence<TsType> = when (type) {
        is TsPrimitiveType -> emptySequence()
        is TsUnknownType -> primitiveTypes
        is TsAnyType -> primitiveTypes
        else -> emptySequence()
    }

    private val topTypeStream by lazy { TsTopTypeStream(this) }

    override fun topTypeStream(): UTypeStream<TsType> = topTypeStream
}

class TsTopTypeStream(
    private val typeSystem: TsTypeSystem,
    private val primitiveTypes: List<TsType> = TsTypeSystem.primitiveTypes.toList(),
    // Currently only TsUnknownType was encountered and viewed as any type.
    // However, there is TsAnyType that represents any type.
    // TODO: replace TsUnknownType with further TsTypeSystem implementation.
    private val anyTypeStream: UTypeStream<TsType> = USupportTypeStream.from(typeSystem, TsUnknownType),
) : UTypeStream<TsType> {

    override fun filterBySupertype(type: TsType): UTypeStream<TsType> {
        if (type is TsPrimitiveType) return emptyTypeStream()

        return anyTypeStream.filterBySupertype(type)
    }

    override fun filterBySubtype(type: TsType): UTypeStream<TsType> {
        return anyTypeStream.filterBySubtype(type)
    }

    override fun filterByNotSupertype(type: TsType): UTypeStream<TsType> {
        if (type in primitiveTypes) {
            val updatedPrimitiveTypes = primitiveTypes.remove(type)

            if (updatedPrimitiveTypes.isEmpty()) return anyTypeStream

            return TsTopTypeStream(typeSystem, updatedPrimitiveTypes, anyTypeStream)
        }

        return TsTopTypeStream(typeSystem, primitiveTypes, anyTypeStream.filterByNotSupertype(type))
    }

    override fun filterByNotSubtype(type: TsType): UTypeStream<TsType> {
        if (type in primitiveTypes) {
            val updatedPrimitiveTypes = primitiveTypes.remove(type)

            if (updatedPrimitiveTypes.isEmpty()) return anyTypeStream

            return TsTopTypeStream(typeSystem, updatedPrimitiveTypes, anyTypeStream)
        }

        return TsTopTypeStream(typeSystem, primitiveTypes, anyTypeStream.filterByNotSubtype(type))
    }

    override fun take(n: Int): TypesResult<TsType> {
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

    override val commonSuperType: TsType?
        get() = TsUnknownType.takeIf { !(isEmpty ?: true) }

    private fun <T> List<T>.remove(x: T): List<T> = this.filterNot { it == x }
}
