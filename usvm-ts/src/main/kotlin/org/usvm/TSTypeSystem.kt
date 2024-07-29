package org.usvm

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.jacodb.ets.base.*
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFile
import org.usvm.types.*
import org.usvm.types.TypesResult.Companion.toTypesResult
import kotlin.time.Duration

class TSTypeSystem(
    override val typeOperationsTimeout: Duration,
    val project: EtsFile,
) : UTypeSystem<EtsType> {

    override fun isSupertype(supertype: EtsType, type: EtsType): Boolean {
        return when {
            supertype == type -> return true
            type is EtsPrimitiveType -> return false
            type is EtsClassType -> supertype.checkSubtype(type.classSignature)
            else -> false
        }
    }

    override fun hasCommonSubtype(type: EtsType, types: Collection<EtsType>): Boolean {
        when (type) {
            is EtsPrimitiveType -> return types.isEmpty()
            is EtsClassType -> return types.all { isSupertype(it, type) }
            is EtsArrayType -> {
                val elementTypes = types.map {
                    when {
                        it is EtsArrayType -> it.elementType
                        else -> return false
                    }
                }
                return hasCommonSubtype(type.elementType, elementTypes)
            }
            else -> TODO()
        }
    }

    override fun isFinal(type: EtsType): Boolean {
        // No final modifier in jacodb IR
        return false
    }

    override fun isInstantiable(type: EtsType): Boolean {
        return true
    }

    override fun findSubtypes(type: EtsType): Sequence<EtsType> = when (type) {
        is EtsPrimitiveType -> emptySequence()
        is EtsArrayType -> findSubtypes(type.elementType).map { EtsArrayType(it, type.dimensions) }
        is EtsClassType -> TODO()
        else -> TODO()
    }

    override fun topTypeStream(): UTypeStream<EtsType> {
        return TSTopTypeStream(this)
    }

    private fun EtsType.checkSubtype(typeClassSignature: EtsClassSignature?): Boolean {
        if (typeClassSignature == null) return false

        return this.typeName == typeClassSignature.name ||
                this.checkSubtype(project.getClassBySignature(typeClassSignature)?.superClass)
    }
}

class TSTopTypeStream(
    private val typeSystem: TSTypeSystem,
    val primitiveTypes: PersistentList<EtsPrimitiveType> = persistentListOf(
        EtsNumberType,
        EtsBooleanType,
        EtsStringType
    ),
) : UTypeStream<EtsType> {
    override fun filterBySupertype(type: EtsType): UTypeStream<EtsType> {
        if (type is EtsPrimitiveType) {
            if (type !in primitiveTypes) {
                return emptyTypeStream()
            }

            return USingleTypeStream(typeSystem, type)
        }

        return emptyTypeStream()
    }

    override fun filterBySubtype(type: EtsType): UTypeStream<EtsType> {
        if (type is EtsPrimitiveType) {
            if (type !in primitiveTypes) {
                return emptyTypeStream()
            }

            return USingleTypeStream(typeSystem, type)
        }

        return emptyTypeStream()
    }

    override fun filterByNotSupertype(type: EtsType): UTypeStream<EtsType> {
        if (type is EtsPrimitiveType && type in primitiveTypes) {
            val updatedPrimitiveTypes = primitiveTypes.remove(type)

            if (updatedPrimitiveTypes.isEmpty()) {
                return emptyTypeStream()
            }

            return TSTopTypeStream(typeSystem, updatedPrimitiveTypes)
        }

        return TSTopTypeStream(typeSystem, primitiveTypes)
    }

    override fun filterByNotSubtype(type: EtsType): UTypeStream<EtsType> {
        if (type is EtsPrimitiveType && type in primitiveTypes) {
            val updatedPrimitiveTypes = primitiveTypes.remove(type)

            if (updatedPrimitiveTypes.isEmpty()) {
                return emptyTypeStream()
            }

            return TSTopTypeStream(typeSystem, updatedPrimitiveTypes)
        }

        return TSTopTypeStream(typeSystem, primitiveTypes)
    }

    override fun take(n: Int): TypesResult<EtsType> {
        return primitiveTypes.take(n).toTypesResult(wasTimeoutExpired = false)
    }

    override val isEmpty: Boolean
        get() = primitiveTypes.isEmpty()

    override val commonSuperType: EtsType
        get() = EtsAnyType

}
